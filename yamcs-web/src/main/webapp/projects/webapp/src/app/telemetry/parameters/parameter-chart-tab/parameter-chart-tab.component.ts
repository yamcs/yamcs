import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { ComponentPortal } from '@angular/cdk/portal';
import {
  AfterViewInit,
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
  BackfillingSubscription,
  BaseComponent,
  ConfigService,
  EnumValue,
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
import { PlotDataSource } from './PlotDataSource';
import { RequestedParameter } from './RequestedParameter';
import { State, TraceState } from './State';
import { ParameterChartTooltipComponent } from './tooltip.component';
import { TraceConfigComponent } from './trace-config.component';
import { TraceConfig } from './TraceConfig';
import { TraceForm } from './TraceForm';

const DEFAULT_RANGE = 'PT15M';
const GRID_COLOR = '#efefef';

/** Minimum content height of a single plot in a stacked layout. */
const MIN_BAND_HEIGHT = 40;

/**
 * 'overlay': all traces in one plot, sharing one Y-axis (the classic layout).
 * 'split': one plot per trace, stacked vertically, each with its own Y-axis,
 *          all sharing the time axis.
 */
export type ChartLayout = 'overlay' | 'split';

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

  /**
   * Deliberately not persisted: every page load starts in overlay layout.
   */
  chartLayout = signal<ChartLayout>('overlay');

  /**
   * Top offset (in px, relative to the timeline container) of each plot in a
   * stacked layout, used to position the per-plot legend chips.
   */
  bandOffsets = signal<{ traceId: string; top: number }[]>([]);

  legend = new Legend();

  private state$ = new BehaviorSubject<State | null>(null);

  private timeRuler: TimeRuler;

  // Shared singletons. Bands are cheap and get recreated on layout or order
  // changes; these are not, so the component owns them.
  private dataSource: PlotDataSource;
  private tooltip: ParameterChartTooltipComponent;
  private tooltipOverlayRef?: OverlayRef;
  private backfillSubscription?: BackfillingSubscription;

  private bands: PlotBand[] = [];
  private bandSignature = '';

  private traceConfigById = new Map<string, TraceConfig>();
  private requestedNameByTraceId = new Map<string, string>();
  private orderedTraceIds: string[] = [];

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
     * It also never rebuilds bands: only their styling is refreshed.
     */
    const formSubscription = this.form.valueChanges.subscribe((fv) => {
      let fetch = false;
      for (const traceForm of fv.traces || []) {
        const traceId = traceForm.traceId!;
        const requestedName = traceForm.parameter!;
        this.legend.setColor(traceId, traceForm.lineColor!);

        const config = this.traceConfigById.get(traceId);
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

        // Enum labels only apply to the engineering value.
        config.enumValues = this.resolveEnumValues(
          config.parameter,
          config.valueType,
        );

        this.dataSource?.addOrUpdateTrace(traceId, config);
      }
      for (const band of this.bands) {
        band.applyTraceConfigs();
      }
      if (fetch) {
        this.dataSource?.reloadVisibleRange();
      }

      this.loadHLines();

      for (const band of this.bands) {
        band.centerZero = fv.centerZero ?? false;
        if (fv.centerZero) {
          band.resetAxisRange();
        }
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

    this.dataSource = new PlotDataSource(
      this.yamcs,
      this.synchronizer,
      this.configService,
    );
    this.setupTooltip();
    this.backfillSubscription =
      this.yamcs.yamcsClient.createBackfillingSubscription(
        { instance: this.yamcs.instance! },
        (update) => {
          if (update.finished) {
            this.dataSource.reloadVisibleRange();
          }
        },
      );

    // Creates the (initially empty) band, plus the TimeRuler
    this.syncBands(true);

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
      // Y-axis zoom is a single-axis concept, only meaningful in overlay layout
      this.bands[0]?.setAxisRange(state.minimum, state.maximum);
    }

    const mouseTracker = new MouseTracker(this.timeline);
    mouseTracker.trackY = true;
    new HoveredDateAnnotation(this.timeline, this.formatter);

    this.resizeObserver = new ResizeObserver(() => this.layoutBands());
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
        this.updateWindow(true /* fetch */);
      });

    this.viewportChange$
      .pipe(
        filter((evt) => evt?.source === 'autoscroll'),
        debounceTime(400),
      )
      .forEach((evt) => {
        this.updateWindow(false /* no fetch */);
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

  private setupTooltip() {
    const bodyRef = new ElementRef(document.body);
    const positionStrategy = this.overlay
      .position()
      .flexibleConnectedTo(bodyRef)
      .withPositions([
        {
          originX: 'start',
          originY: 'top',
          overlayX: 'start',
          overlayY: 'top',
        },
      ])
      .withPush(false);

    this.tooltipOverlayRef = this.overlay.create({ positionStrategy });
    const tooltipPortal = new ComponentPortal(ParameterChartTooltipComponent);
    this.tooltip = this.tooltipOverlayRef.attach(tooltipPortal).instance;
  }

  private updateWindow(fetch: boolean) {
    this.dataSource?.updateWindow(
      new Date(this.timeline.start),
      new Date(this.timeline.stop),
      fetch,
    );
  }

  setLayout(layout: ChartLayout) {
    if (this.chartLayout() !== layout) {
      this.chartLayout.set(layout);
      this.syncBands();
    }
  }

  /**
   * Aligns the set of plot bands with the current layout and trace order.
   *
   * Bands are only recreated when their composition actually changes, since
   * @fqqb/timeline has no reorder API: the whole stack (including the
   * TimeRuler, which must stay at the bottom) has to be torn down and
   * rebuilt in order. Because the data source is shared and its `data$` is a
   * BehaviorSubject, a rebuild triggers no new requests.
   */
  private syncBands(force = false) {
    if (!this.timeline) {
      return;
    }

    let groups: string[][] =
      this.chartLayout() === 'split'
        ? this.orderedTraceIds.map((traceId) => [traceId])
        : [this.orderedTraceIds];
    if (!groups.length) {
      // No resolved trace yet: keep one empty plot rather than a blank canvas
      groups = [[]];
    }

    const signature = this.chartLayout() + '|' + groups.map((g) => g.join(',')).join(';');
    if (!force && signature === this.bandSignature) {
      // Same composition: only the trace configs may have changed.
      for (let i = 0; i < this.bands.length; i++) {
        this.bands[i].setTraces(groups[i] ?? [], this.traceConfigById);
      }
      this.loadHLines();
      this.layoutBands();
      return;
    }
    this.bandSignature = signature;

    for (const band of this.bands) {
      this.timeline.removeChild(band);
    }
    this.bands = [];
    if (this.timeRuler) {
      this.timeline.removeChild(this.timeRuler);
    }

    const headerBackground = utils.getCssVariable('--y-background-color');
    for (const traceIds of groups) {
      const band = new PlotBand(
        this.timeline,
        this.dataSource,
        this.tooltip,
        this.legend,
      );
      band.headerBackground = headerBackground;
      band.grid = 'underlay';
      band.gridColor = GRID_COLOR;
      band.axisTickLength = 0;
      band.labelPadding = 4;
      band.centerZero = this.form.value.centerZero ?? false;
      band.addMutationListener(() => this.updateState());
      band.setTraces(traceIds, this.traceConfigById);
      this.bands.push(band);
    }

    // Recreated last, so that it renders below every plot
    this.createTimeRuler(headerBackground);

    this.loadHLines();
    this.layoutBands();
  }

  private createTimeRuler(headerBackground: string) {
    this.timeRuler = new TimeRuler(this.timeline);
    if (this.formatter.utc()) {
      this.timeRuler.timezone = 'UTC';
    }
    this.timeRuler.grid = 'underlay';
    this.timeRuler.gridColor = GRID_COLOR;
    this.timeRuler.headerBackground = headerBackground;
    this.timeRuler.background = headerBackground;
    this.timeRuler.label = new Date(this.timeline.start)
      .toISOString()
      .substring(0, 10);
  }

  /**
   * Divides the available vertical space over the plots, and recomputes the
   * offsets used to position the per-plot legend chips in a stacked layout.
   */
  private layoutBands() {
    if (!this.bands.length || !this.timeRuler) {
      return;
    }

    // One border below each band, plus one above the whole stack
    const available =
      this.timeline.height - this.timeRuler.contentHeight - 1 - this.bands.length;
    const contentHeight = Math.max(
      MIN_BAND_HEIGHT,
      Math.floor(available / this.bands.length),
    );

    const offsets: { traceId: string; top: number }[] = [];
    let top = 0;
    for (const band of this.bands) {
      band.contentHeight = contentHeight;

      // Update fill style
      band.onResize();

      const traceId = band.getTraceIds()[0];
      if (traceId) {
        offsets.push({ traceId, top });
      }
      // paddingTop/paddingBottom default to 0, bandBorderWidth to 1
      top += contentHeight + this.timeline.bandBorderWidth;
    }
    this.bandOffsets.set(offsets);
  }

  private updateLegendValues() {
    for (const item of this.legend.getItems()) {
      const band = this.bandForTrace(item.traceId);
      const rtValue = band?.getParameterValue(item.traceId);

      if (rtValue !== undefined) {
        item.value.set(band!.getValueLabel(item.traceId, rtValue));
      } else {
        item.value.set(null);
      }
    }
  }

  private bandForTrace(traceId: string) {
    return this.bands.find((band) => band.hasTrace(traceId));
  }

  /**
   * Applies horizontal lines to every plot.
   *
   * The zero line applies to all plots. Alarm thresholds belong to the
   * parameter in the URL, so in a stacked layout they are only drawn on the
   * plot that actually shows that parameter.
   */
  private loadHLines() {
    const zeroLines: HLine[] = [];
    const { value: fv } = this.form;

    if (fv.showZeroLine) {
      zeroLines.push({
        value: 0,
        lineColor: 'black',
        lineDash: [4, 3],
        extendAxisRange: true,
      });
    }

    const alarmLines: HLine[] = [];
    const mainParameter = this.definitionCache.get(this.qualifiedName());
    if (fv.showAlarmThresholds && mainParameter?.type?.defaultAlarm) {
      const { defaultAlarm } = mainParameter.type;
      const ranges = defaultAlarm.staticAlarmRanges || [];
      for (const range of ranges) {
        const min = range.minInclusive ?? range.minExclusive;
        const max = range.maxInclusive ?? range.maxExclusive;
        if (min !== undefined) {
          alarmLines.push({
            value: min,
            lineColor: this.colorForLevel(range.level) || 'black',
            lineDash: [4, 3],
            label: `${range.level.toLowerCase()} low`,
            labelBackground: this.colorForLevel(range.level) || 'black',
            labelTextColor: 'white',
          });
        }
        if (max !== undefined) {
          alarmLines.push({
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

    for (const band of this.bands) {
      const showAlarms =
        this.bands.length === 1 ||
        band
          .getTraceIds()
          .some(
            (traceId) =>
              this.requestedNameByTraceId.get(traceId) === this.qualifiedName(),
          );
      band.hlines = showAlarms ? [...zeroLines, ...alarmLines] : zeroLines;
    }
  }

  /**
   * Reads the form, and applies its state to the PlotBands and Legend.
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
          const valueType = traceState?.valueType ?? 'engineering';
          const enumValues = this.resolveEnumValues(parameter, valueType);
          const enumTrace = !!enumValues;

          // Enum traces default to a step line. Keep the plain default when the
          // user has not touched the control; respect any explicit choice.
          const lineStyleControl =
            traceFormsById.get(traceId)!.controls.lineStyle;
          const useEnumStepDefault =
            enumTrace &&
            lineStyleControl.pristine &&
            lineStyleControl.value === 'straight';
          const lineStyle = useEnumStepDefault
            ? 'step'
            : (traceState?.lineStyle ?? 'straight');
          if (useEnumStepDefault) {
            lineStyleControl.setValue('step', { emitEvent: false });
          }

          const trace: TraceConfig = {
            parameter,
            color,
            lineWidth: traceState?.lineWidth ?? 2,
            lineStyle,
            fill: traceState?.fill ?? false,
            valueType,
            enumValues,
          };
          this.traceConfigById.set(traceId, trace);
          this.requestedNameByTraceId.set(traceId, requestedName);
          this.dataSource.addOrUpdateTrace(traceId, trace);

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
          this.traceConfigById.delete(traceId);
          this.requestedNameByTraceId.delete(traceId);
          this.dataSource.removeTrace(traceId);

          const error = 'Parameter not found';
          this.legend.addItem(traceId, label, color, null, error);
        }
      }
      this.applyOrder();
      this.loadHLines();
    });

    this.updateState();
    this.prevRequestedNames = requestedNames;
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
    for (const band of this.bands) {
      band.resetAxisRange();
    }
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

  legendItemFor(traceId: string) {
    return this.legend.itemsSignal().find((item) => item.traceId === traceId);
  }

  /**
   * Enum value↔label table for a trace, shown as a read-only key in the detail
   * pane. Returns undefined for numeric traces or a trace showing raw values.
   */
  enumValuesForTrace(traceForm: FormGroup<TraceForm>) {
    const traceId = traceForm.value.traceId;
    return traceId ? this.traceConfigById.get(traceId)?.enumValues : undefined;
  }

  /**
   * Ordinal↔label keys for every enum trace on the chart, rendered as an
   * always-visible overlay next to the plot so the Y-axis ordinals can be read
   * off against their enumeration labels.
   */
  enumKeys(): {
    traceId: string;
    name: string;
    color: string;
    values: EnumValue[];
  }[] {
    const keys: {
      traceId: string;
      name: string;
      color: string;
      values: EnumValue[];
    }[] = [];
    for (const traceForm of this.traces.controls) {
      const traceId = traceForm.value.traceId;
      if (!traceId) {
        continue;
      }
      const trace = this.traceConfigById.get(traceId);
      if (trace?.enumValues?.length) {
        keys.push({
          traceId,
          name: trace.parameter.name,
          color: trace.color,
          values: trace.enumValues,
        });
      }
    }
    return keys;
  }

  /**
   * Value↔label table for a categorical engineering trace, used both for the
   * Y-axis label formatter and the on-chart key. Covers enumerations (ordinals
   * are serialized as strings by the MDB API, so coerce) and booleans (a
   * synthetic 0/1 table from the type's zero/one string values). Returns
   * undefined for a numeric parameter or a trace showing the raw value.
   */
  private resolveEnumValues(
    parameter: Parameter,
    valueType: string,
  ): EnumValue[] | undefined {
    const type = parameter.type;
    if (valueType !== 'engineering' || !type) {
      return undefined;
    }
    if (type.engType === 'enumeration' && type.enumValues?.length) {
      return type.enumValues.map((ev) => ({ ...ev, value: Number(ev.value) }));
    }
    if (type.engType === 'boolean') {
      return [
        { value: 0, label: type.zeroStringValue || 'FALSE' },
        { value: 1, label: type.oneStringValue || 'TRUE' },
      ];
    }
    return undefined;
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
    // Sufficiently random identifier
    const traceId: string =
      Math.random().toString(36).substring(2, 10) +
      Math.random().toString(36).substring(2, 10);

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
    // Y-axis zoom is a single-axis concept, only tracked for the overlay layout
    const overlayBand = this.bands.length === 1 ? this.bands[0] : undefined;
    const customMinimum = overlayBand?.customMinimum;
    const customMaximum = overlayBand?.customMaximum;
    const hasCustomMinimum = customMinimum !== undefined;
    const hasCustomMaximum = customMaximum !== undefined;

    this.resetZoomEnabled.set(
      this.bands.some(
        (band) =>
          band.customMinimum !== undefined || band.customMaximum !== undefined,
      ),
    );

    const { value: fv } = this.form;
    const state: State = {
      minimum: hasCustomMinimum ? customMinimum : undefined,
      maximum: hasCustomMaximum ? customMaximum : undefined,
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
    const traceId = traceForm.value.traceId!;

    this.traceConfigById.delete(traceId);
    this.requestedNameByTraceId.delete(traceId);
    this.dataSource?.removeTrace(traceId);
    this.legend.removeItem(traceId);

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
   * bands, band lines and legend items.
   */
  private applyOrder() {
    let traceIds: string[] = [];
    for (let i = 0; i < this.traces.length; i++) {
      traceIds.push(this.traces.at(i).value.traceId!);
    }

    this.orderedTraceIds = traceIds.filter((id) =>
      this.traceConfigById.has(id),
    );
    this.syncBands();
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
    this.tooltipOverlayRef?.dispose();
    this.backfillSubscription?.cancel();
    this.dataSource?.disconnect();
    this.timeline?.disconnect();
  }
}
