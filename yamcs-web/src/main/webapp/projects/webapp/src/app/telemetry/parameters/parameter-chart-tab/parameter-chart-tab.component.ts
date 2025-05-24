import { Overlay } from '@angular/cdk/overlay';
import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  input,
  OnDestroy,
  OnInit,
  signal,
  viewChild,
} from '@angular/core';
import { FormArray, FormControl, FormGroup } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute } from '@angular/router';
import {
  DefaultSidebar,
  HLine,
  MouseTracker,
  Timeline,
  TimeRuler,
  ViewportChangeEvent,
} from '@fqqb/timeline';
import {
  BaseComponent,
  ConfigService,
  Formatter,
  Parameter,
  utils,
  WebappSdkModule,
} from '@yamcs/webapp-sdk';
import {
  BehaviorSubject,
  debounceTime,
  distinctUntilChanged,
  filter,
  map,
  Subject,
  Subscription,
  throttleTime,
} from 'rxjs';
import { SelectRangeDialogComponent } from '../select-range-dialog/select-range-dialog.component';
import { HoveredDateAnnotation } from './HoveredDateAnnotation';
import { Legend } from './Legend';
import { LegendComponent } from './legend.component';
import { PlotBand } from './PlotBand';
import { RequestedParameter } from './RequestedParameter';
import { State, TraceState } from './State';
import { TraceConfigComponent } from './trace-config.component';
import { TraceConfig } from './TraceConfig';
import { TraceForm } from './TraceForm';

const DEFAULT_RANGE = 'PT15M';
const GRID_COLOR = '#efefef';

export const DEFAULT_COLORS = [
  '#1b73e8',
  '#129eaf',
  '#d01984',
  '#34a853',
  '#7626bb',
  '#e64b19',
];

@Component({
  selector: 'app-parameter-chart-tab',
  templateUrl: './parameter-chart-tab.component.html',
  styleUrls: ['./parameter-chart-tab.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LegendComponent, TraceConfigComponent, WebappSdkModule],
})
export class ParameterChartTabComponent
  extends BaseComponent
  implements OnInit, AfterViewInit, OnDestroy
{
  qualifiedName = input.required<string>({ alias: 'parameter' });

  container = viewChild.required<ElementRef<HTMLDivElement>>('container');
  private resizeObserver?: ResizeObserver;

  timeline: Timeline;

  // null => a custom range (start/stop) is being viewed
  // string-encoded number => millisecond duration of the viewport
  // string => ISO duration of the viewport
  //
  // If not null, perform autoscroll
  range = signal<string | null>(DEFAULT_RANGE);
  start = signal<Date | null>(null);
  stop = signal<Date | null>(null);

  private viewportChange$ = new BehaviorSubject<ViewportChangeEvent | null>(
    null,
  );

  sidebarWidth = signal<number>(0);
  resetZoomEnabled = signal<boolean>(false);

  legend = new Legend();

  private state$ = new BehaviorSubject<State | null>(null);

  private timeRuler: TimeRuler;
  private band: PlotBand;
  readonly gridColor = GRID_COLOR;

  private urlUpdate$ = new Subject<void>();
  private subscriptions: Subscription[] = [];

  form = new FormGroup({
    showZeroLine: new FormControl(false),
    showAlarmThresholds: new FormControl(true),
    centerZero: new FormControl(false),
    traces: new FormArray<FormGroup<TraceForm>>([]),
  });

  // Keep track of the last names that were actually
  // requested. Set at the end of reloadForm()
  private prevRequestedNames: string[] = [];

  // Local cache of Parameter definitions by requested name.
  // A null value means a request was made, without result.
  private definitionCache = new Map<string, Parameter | null>();

  constructor(
    readonly route: ActivatedRoute,
    private dialog: MatDialog,
    private formatter: Formatter,
    private configService: ConfigService,
    private overlay: Overlay,
  ) {
    super();

    const navigationSubscription = this.urlUpdate$
      .pipe(debounceTime(300))
      .subscribe(() => {
        this.router.navigate([], {
          replaceUrl: true,
          relativeTo: this.route,
          queryParams: {
            range: this.range(),
            start: this.start()?.toISOString() ?? null,
            stop: this.stop()?.toISOString() ?? null,
          },
          queryParamsHandling: 'merge',
        });
      });
    this.subscriptions.push(navigationSubscription);

    const syncSubscription = this.synchronizer.sync(() =>
      this.updateLegendValues(),
    );
    this.subscriptions.push(syncSubscription);

    const stateSubscription = this.state$
      .pipe(debounceTime(300))
      .subscribe(() => {
        const state = this.state$.value;
        if (state) {
          const json = JSON.stringify(state);
          this.router.navigate([], {
            replaceUrl: true,
            relativeTo: this.route,
            queryParams: {
              state: json,
            },
            queryParamsHandling: 'merge',
          });
        }
      });
    this.subscriptions.push(stateSubscription);

    /*
     * Delay-detect when requested parameter names have changed.
     * If yes, reload the form.
     */
    const nameSubscription = this.form.valueChanges
      .pipe(
        map((fv) => {
          const requestedNames: string[] = (fv.traces || []).map(
            (trace: any) => trace.parameter,
          );
          return requestedNames;
        }),
        distinctUntilChanged((a, b) => utils.deepEquals(a, b)),
        debounceTime(500),
      )
      .subscribe((curr) => {
        // Recheck after the debounce, to avoid
        // unnecessary requests (especially on page init)
        if (!utils.deepEquals(this.prevRequestedNames, curr)) {
          this.reloadForm();
        }
      });
    this.subscriptions.push(nameSubscription);

    /**
     * Instantly detect changes to any form controls.
     *
     * Re-apply configuration for each trace. This is usually an
     * offline operation, except if the change involves raw-to-eng
     * or eng-to-raw, which requires a new sample request.
     *
     * This subscription does not handle changes to trace parameters.
     */
    const formSubscription = this.form.valueChanges.subscribe((fv) => {
      let fetch = false;
      for (const traceForm of fv.traces || []) {
        const traceId = traceForm.traceId!;
        const requestedName = traceForm.parameter!;
        this.legend.setColor(traceId, traceForm.lineColor!);

        const config = this.band.getTrace(traceId);
        if (!config) {
          continue;
        }

        config.color = traceForm.lineColor!;
        config.lineWidth = traceForm.lineWidth!;
        config.lineStyle = traceForm.lineStyle!;
        config.fill = traceForm.fill!;

        if (traceForm.valueType === 'raw') {
          this.legend.setShowUnits(traceId, false);
          this.legend.setLabel(traceId, `RAW('${requestedName}')`);
        } else {
          this.legend.setShowUnits(traceId, true);
          this.legend.setLabel(traceId, requestedName);
        }

        // Whether to fetch a new set of data
        fetch ||= traceForm.valueType! !== config.valueType;
        config.valueType = traceForm.valueType!;
      }
      this.band.applyTraceConfigs();
      if (fetch) {
        this.band.updateWindow(true);
      }

      this.loadHLines();

      this.band.centerZero = fv.centerZero ?? false;
      if (fv.centerZero) {
        this.band.resetAxisRange();
      }
    });
    this.subscriptions.push(formSubscription);
    this.readQueryParams();
  }

  private readQueryParams() {
    const { queryParamMap } = this.route.snapshot;
    const range = queryParamMap.get('range');
    let start = queryParamMap.get('start');
    let stop = queryParamMap.get('stop');
    if (!range) {
      if (start && stop) {
        this.range.set(null);
        this.start.set(utils.toDate(start));
        this.stop.set(utils.toDate(stop));
      } else {
        this.range.set(DEFAULT_RANGE);
      }
    } else {
      this.range.set(range || DEFAULT_RANGE);
    }

    const stateJson = queryParamMap.get('state');
    if (stateJson) {
      this.state$.next(JSON.parse(stateJson));
    }

    // Ensure URL matches current set of derived parameters
    this.updateURL(true);
  }

  ngOnInit(): void {
    // Autoscroll (don't care about data)
    const timeSubscription = this.yamcs.time$
      .pipe(throttleTime(1000))
      .subscribe(() => this.autoscroll());
    this.subscriptions.push(timeSubscription);

    this.openDetailPane();

    // Alarm thresholds are shown for the parameter specified in the
    // url, regardless of the trace form.
    //
    // Populate the definition cache, then reload HLines.
    this.fetchParameter(0, '', this.qualifiedName()).then((result) => {
      if (result.parameter) {
        this.loadHLines();
      }
    });
  }

  private autoscroll() {
    const range = this.range();
    if (range && this.timeline) {
      const stop = this.yamcs.getMissionTime();
      let start: Date;
      if (range.startsWith('P')) {
        start = utils.subtractDuration(stop, range);
      } else {
        start = new Date(stop.getTime() - Number(range));
      }
      this.timeline.setViewRange(start.getTime(), stop.getTime(), {
        source: 'autoscroll',
        animate: false,
      });
    }
  }

  ngAfterViewInit(): void {
    // Reapply state where possible
    const state = this.state$.value;

    this.timeline = new Timeline(this.container().nativeElement);
    this.timeline.fontFamily = 'Roboto, sans-serif';
    this.timeline.yOverflow = 'hidden';

    const sidebar = new DefaultSidebar(this.timeline);
    sidebar.width = 75;
    sidebar.foregroundColor = 'grey';
    sidebar.fontFamily = 'Roboto, sans-serif';
    sidebar.textAlignment = 'middle';

    this.sidebarWidth.set(sidebar.width);
    sidebar.addResizeListener((ev) => {
      this.sidebarWidth.set(ev.width);
    });
    this.timeline.leftSidebar = sidebar;

    const headerBackground = utils.getCssVariable('--y-background-color');

    this.band = new PlotBand(
      this.timeline,
      this.yamcs,
      this.synchronizer,
      this.configService,
      this.overlay,
      this.legend,
    );
    this.band.headerBackground = headerBackground;
    this.band.grid = 'underlay';
    this.band.gridColor = GRID_COLOR;
    this.band.axisTickLength = 0;
    this.band.labelPadding = 4;
    this.band.addMutationListener(() => this.updateState());

    if (state?.centerZero !== undefined) {
      this.form.patchValue({ centerZero: state.centerZero });
    }
    if (state?.showZeroLine !== undefined) {
      this.form.patchValue({ showZeroLine: state.showZeroLine });
    }
    if (state?.showAlarmThresholds !== undefined) {
      this.form.patchValue({
        showAlarmThresholds: state.showAlarmThresholds,
      });
    }
    if (state?.minimum !== undefined && state.maximum !== undefined) {
      this.band.setAxisRange(state.minimum, state.maximum);
    }

    this.timeRuler = new TimeRuler(this.timeline);
    if (this.formatter.utc()) {
      this.timeRuler.timezone = 'UTC';
    }
    this.timeRuler.grid = 'underlay';
    this.timeRuler.gridColor = GRID_COLOR;
    this.timeRuler.headerBackground = headerBackground;
    this.timeRuler.background = headerBackground;

    const mouseTracker = new MouseTracker(this.timeline);
    mouseTracker.trackY = true;
    new HoveredDateAnnotation(this.timeline, this.formatter);

    this.resizeObserver = new ResizeObserver(() => {
      // Expand plot area to cover full height (minus borders and x-axis)
      this.band.contentHeight =
        this.timeline.height - this.timeRuler.contentHeight - 1 - 1;

      // Update fill style
      this.band.onResize();
    });
    this.resizeObserver.observe(this.container().nativeElement);

    this.timeline.addViewportChangeListener((event) => {
      const start = new Date(event.start);
      const stop = new Date(event.stop);

      const day = start.toISOString().substring(0, 10);
      this.timeRuler.label = day;

      if (event.source !== 'autoscroll') {
        this.setRangeQueryParams(null, start, stop);
      }

      this.viewportChange$.next(event);
    });

    // Filter before debounce [!]
    this.viewportChange$
      .pipe(
        filter((evt) => evt?.source !== 'autoscroll'),
        debounceTime(400),
      )
      .forEach((evt) => {
        this.band.updateWindow(true /* fetch */);
      });

    this.viewportChange$
      .pipe(
        filter((evt) => evt?.source === 'autoscroll'),
        debounceTime(400),
      )
      .forEach((evt) => {
        this.band.updateWindow(false /* no fetch */);
      });

    if (!this.range()) {
      const start = this.start()!.getTime();
      const stop = this.stop()!.getTime();
      this.timeline.setViewRange(start, stop);
    } else {
      this.autoscroll();
    }

    if (state?.traces?.length) {
      for (let i = 0; i < state.traces.length; i++) {
        const trace = state.traces[i];
        this.addParameterForm(trace.parameter, i - 1, trace);
      }
    } else {
      const requestedName = this.qualifiedName();
      this.addParameterForm(requestedName);
    }

    // Trigger an immediate form reload, so that we don't
    // have to wait until the first debounce trigger.
    this.reloadForm();
  }

  /**
   * Reads the form, and applies its state to the PlotBand and Legend.
   *
   * This operation will re-query MDB definitions and data within the
   * current window.
   */
  private reloadForm() {
    const traces = this.form.value.traces || [];
    const requestedNames = traces.map((trace: any) => trace.parameter);

    const traceFormsById = new Map<string, FormGroup<TraceForm>>();
    const promises: Promise<RequestedParameter>[] = [];
    for (let i = 0; i < this.traces.controls.length; i++) {
      const traceForm = this.traces.controls.at(i)!;
      if (traceForm.value.parameter) {
        const traceId = traceForm.value.traceId!;
        traceFormsById.set(traceId, traceForm as FormGroup<TraceForm>);

        const promise = this.fetchParameter(
          i,
          traceId,
          traceForm.value.parameter,
        );
        promises.push(promise);
      }
    }

    // Important to preserve the original order, regardless
    // of the order in which the requests are fulfilled.
    Promise.all(promises).then((requests) => {
      for (let i = 0; i < requests.length; i++) {
        const request = requests[i];
        const hexColor = DEFAULT_COLORS[request.index % DEFAULT_COLORS.length];

        const requestedName = request.requestedName;
        const traceId = request.traceId;
        const traceState = traceFormsById.get(traceId)!.value;
        const color = traceState?.lineColor ?? hexColor;
        const parameter = request.parameter;

        const label =
          traceState?.valueType === 'raw'
            ? `RAW('${requestedName}')`
            : requestedName;

        if (parameter) {
          const trace: TraceConfig = {
            parameter,
            color,
            lineWidth: traceState?.lineWidth ?? 2,
            lineStyle: traceState?.lineStyle ?? 'straight',
            fill: traceState?.fill ?? false,
            valueType: traceState?.valueType ?? 'engineering',
          };
          this.band.addOrUpdateTrace(traceId, trace);

          const units = utils.getUnits(parameter.type?.unitSet);
          this.legend.addItem(traceId, label, color, units, null);
          this.legend.setShowUnits(
            traceId,
            traceState?.valueType === 'engineering',
          );
        } else {
          // Parameter not found.
          //
          // Remove plot line, but do show it in the legend.
          this.band.removeTrace(traceId);

          const error = 'Parameter not found';
          this.legend.addItem(traceId, label, color, null, error);
        }
      }
      this.loadHLines();
      this.applyOrder();

      //this.band.updateWindow(true);
    });

    this.updateState();
    this.prevRequestedNames = requestedNames;
  }

  private updateLegendValues() {
    for (const item of this.legend.getItems()) {
      const rtValue = this.band.getParameterValue(item.traceId);

      if (rtValue !== undefined) {
        item.value.set(String(rtValue));
      } else {
        item.value.set(null);
      }
    }
  }

  private loadHLines() {
    const hlines: HLine[] = [];
    const { value: fv } = this.form;

    if (fv.showZeroLine) {
      hlines.push({
        value: 0,
        lineColor: 'black',
        lineDash: [4, 3],
        extendAxisRange: true,
      });
    }

    const mainParameter = this.definitionCache.get(this.qualifiedName());
    if (fv.showAlarmThresholds && mainParameter?.type?.defaultAlarm) {
      const { defaultAlarm } = mainParameter.type;
      const ranges = defaultAlarm.staticAlarmRanges || [];
      for (const range of ranges) {
        const min = range.minInclusive ?? range.minExclusive;
        const max = range.maxInclusive ?? range.maxExclusive;
        if (min !== undefined) {
          hlines.push({
            value: min,
            lineColor: this.colorForLevel(range.level) || 'black',
            lineDash: [4, 3],
            label: `${range.level.toLowerCase()} low`,
            labelBackground: this.colorForLevel(range.level) || 'black',
            labelTextColor: 'white',
          });
        }
        if (max !== undefined) {
          hlines.push({
            value: max,
            lineColor: this.colorForLevel(range.level) || 'black',
            lineDash: [4, 3],
            label: `${range.level.toLowerCase()} high`,
            labelBackground: this.colorForLevel(range.level) || 'black',
            labelTextColor: 'white',
          });
        }
      }
    }

    this.band.hlines = hlines;
  }

  loadLatest(range: string) {
    const stop = this.yamcs.getMissionTime();
    const start = utils.subtractDuration(stop, range);
    this.timeline.setViewRange(start.getTime(), stop.getTime());
    this.setRangeQueryParams(range, null, null);
  }

  /**
   * Place mission time at the right, preserving current viewport range
   */
  jumpToNow() {
    const range = Math.round(this.timeline.stop - this.timeline.start);

    const missionTime = this.yamcs.getMissionTime();
    const start = missionTime.getTime() - range;
    const stop = start + range;

    this.timeline.setViewRange(start, stop);
    this.setRangeQueryParams(String(range), null, null);
  }

  private setRangeQueryParams(
    range: string | null,
    start: Date | null,
    stop: Date | null,
  ) {
    this.range.set(range);
    this.start.set(start);
    this.stop.set(stop);
    this.updateURL(false);
  }

  zoomIn() {
    this.timeline.zoomIn();
  }

  zoomOut() {
    this.timeline.zoomOut();
  }

  autoScale() {
    this.band.resetAxisRange();
  }

  openDateRangeDialog() {
    const start = new Date(this.timeline.start);
    const stop = new Date(this.timeline.stop);
    this.dialog
      .open(SelectRangeDialogComponent, {
        width: '400px',
        data: { start, stop },
      })
      .afterClosed()
      .subscribe((result) => {
        if (result) {
          const { start, stop } = result;
          this.timeline.setViewRange(start.getTime(), stop.getTime());
        }
      });
  }

  get traces() {
    return this.form.controls['traces'] as FormArray<FormGroup<TraceForm>>;
  }

  /**
   * Attempts to find the Parameter definition for a provided
   * qualified name.
   *
   * The provided name may contain aggray offsets, the parameter
   * will then be of the host.
   *
   * This method does not fail if the Parameter is not found.
   */
  private fetchParameter(
    index: number,
    traceId: string,
    requestedName: string,
  ): Promise<RequestedParameter> {
    const parameter = this.definitionCache.get(requestedName);
    if (parameter === null /* requested, but not found */) {
      return Promise.resolve({ index, traceId, requestedName });
    } else if (parameter) {
      return Promise.resolve({ index, traceId, requestedName, parameter });
    }
    return this.yamcs.yamcsClient
      .getParameter(this.yamcs.instance!, requestedName)
      .then((parameter) => {
        this.definitionCache.set(requestedName, parameter);
        return { index, traceId, requestedName, parameter };
      })
      .catch((err) => {
        this.definitionCache.set(requestedName, null);
        return { index, traceId, requestedName };
      });
  }

  private addParameterForm(
    requestedName: string,
    index?: number,
    traceState?: TraceState,
  ) {
    const traceId: string = crypto.randomUUID();

    const lookupIndex = index === undefined ? 0 : index + 1;
    const hexColor = DEFAULT_COLORS[lookupIndex % DEFAULT_COLORS.length];

    const traceForm: FormGroup<TraceForm> = new FormGroup({
      traceId: new FormControl(traceId, { nonNullable: true }),
      parameter: new FormControl(requestedName, { nonNullable: true }),
      lineColor: new FormControl(traceState?.color ?? hexColor, {
        nonNullable: true,
      }),
      fill: new FormControl(traceState?.fill ?? false, { nonNullable: true }),
      lineWidth: new FormControl(traceState?.lineWidth ?? 2, {
        nonNullable: true,
      }),
      lineStyle: new FormControl(traceState?.lineStyle ?? 'straight', {
        nonNullable: true,
      }),
      valueType: new FormControl(traceState?.valueType ?? 'engineering', {
        nonNullable: true,
      }),
    });

    // Add to the form, this will generate a form subscription callback,
    // which is used to make any further requests.
    if (index !== undefined) {
      this.traces.insert(index + 1, traceForm);
    } else {
      this.traces.push(traceForm);
    }
    return traceForm;
  }

  private updateURL(immediate: boolean) {
    if (immediate) {
      this.doUpdateURL();
    } else {
      this.urlUpdate$.next();
    }
  }

  private doUpdateURL() {
    this.router.navigate([], {
      replaceUrl: true,
      relativeTo: this.route,
      queryParams: {
        range: this.range(),
        start: this.start()?.toISOString() ?? null,
        stop: this.stop()?.toISOString() ?? null,
      },
      queryParamsHandling: 'merge',
    });
  }

  private updateState() {
    const { customMinimum, customMaximum } = this.band;
    const hasCustomMinimum = customMinimum !== undefined;
    const hasCustomMaximum = customMaximum !== undefined;

    this.resetZoomEnabled.set(hasCustomMinimum || hasCustomMaximum);

    const { value: fv } = this.form;
    const state: State = {
      minimum: hasCustomMinimum ? this.band.customMinimum : undefined,
      maximum: hasCustomMaximum ? this.band.customMaximum : undefined,
      centerZero: fv.centerZero!,
      showZeroLine: fv.showZeroLine!,
      showAlarmThresholds: fv.showAlarmThresholds!,
      traces: [],
    };

    for (const trace of fv.traces || []) {
      state.traces!.push({
        parameter: trace.parameter!,
        color: trace.lineColor!,
        lineWidth: trace.lineWidth!,
        lineStyle: trace.lineStyle!,
        fill: trace.fill!,
        valueType: trace.valueType!,
      });
    }

    const prev = this.state$.value;
    if (!utils.deepEquals(prev, state)) {
      this.state$.next(state);
    }
  }

  private colorForLevel(level: string) {
    switch (level) {
      case 'WATCH':
        return '#ff8c00';
      case 'WARNING':
        return '#ff8c00';
      case 'DISTRESS':
        return '#f00';
      case 'CRITICAL':
        return '#f00';
      case 'SEVERE':
        return '#f00';
      default:
        console.error('Unknown level ' + level);
    }
  }

  addTrace(index?: number) {
    this.addParameterForm('', index);
    this.applyOrder();
  }

  removeTrace(index: number) {
    const traceForm = this.traces.at(index);
    this.band.removeTrace(traceForm.value.traceId!);
    this.legend.removeItem(traceForm.value.traceId!);

    this.traces.removeAt(index);
    this.applyOrder();
  }

  moveUp(index: number) {
    const traceForm = this.traces.at(index);
    this.traces.removeAt(index);
    this.traces.insert(index - 1, traceForm);

    this.applyOrder();
  }

  moveDown(index: number) {
    const traceForm = this.traces.at(index);
    this.traces.removeAt(index);
    this.traces.insert(index + 1, traceForm);

    this.applyOrder();
  }

  /**
   * Use the form array order as the source to reorder
   * band lines and legend items.
   */
  private applyOrder() {
    let traceIds: string[] = [];
    for (let i = 0; i < this.traces.length; i++) {
      traceIds.push(this.traces.at(i).value.traceId!);
    }

    this.band.applyOrder(traceIds);
    this.legend.applyOrder(traceIds);
  }

  saveImage() {
    const a = document.createElement('a');
    try {
      a.href = this.timeline.toDataURL();
      a.download = 'chart.png';
      document.body.appendChild(a);
      a.click();
    } finally {
      document.body.removeChild(a);
    }
  }

  ngOnDestroy(): void {
    this.resizeObserver?.disconnect();
    this.viewportChange$.complete();
    this.urlUpdate$.complete();
    this.subscriptions.forEach((s) => s.unsubscribe());
    this.timeline?.disconnect();
  }
}
