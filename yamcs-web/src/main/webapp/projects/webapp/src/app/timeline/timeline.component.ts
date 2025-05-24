import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { ComponentPortal } from '@angular/cdk/portal';
import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostListener,
  OnDestroy,
  ViewChild,
  effect,
  inject,
} from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import {
  Band,
  Banner,
  ItemBand as DefaultItemBand,
  DefaultSidebar,
  Item,
  MouseTracker,
  TimeLocator,
  TimeRange,
  Timeline,
} from '@fqqb/timeline';
import {
  AuthService,
  ConfigService,
  Formatter,
  MessageService,
  Preferences,
  Synchronizer,
  TimelineBand,
  TimelineItem,
  TimelineView,
  UpdateTimelineViewRequest,
  WebSocketCall,
  WebappSdkModule,
  YaTooltip,
  YamcsService,
  utils,
} from '@yamcs/webapp-sdk';
import { addHours, addMinutes } from 'date-fns';
import { BehaviorSubject, Subscription } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { BandBase } from './bands/BandBase';
import { CommandBand } from './bands/command-band/CommandBand';
import { ItemBand } from './bands/item-band/ItemBand';
import { ParameterPlot } from './bands/parameter-plot/ParameterPlot';
import { ParameterStateBand } from './bands/parameter-states/ParameterStateBand';
import { TimeRuler } from './bands/time-ruler/TimeRuler';
import { CreateEventDialogComponent } from './dialogs/create-event-dialog.component';
import { CreateTaskDialogComponent } from './dialogs/create-task-dialog.component';
import { JumpToDialogComponent } from './dialogs/jump-to-dialog.component';
import { SelectBandDialogComponent } from './dialogs/select-band-dialog.component';
import { HoveredDateAnnotation } from './HoveredDateAnnotation';
import { PREF_DETAIL_HEIGHT, PREF_VIEW } from './preferences';
import { TimelineDetail } from './timeline-detail/timeline-detail.component';
import { TimelineService } from './timeline.service';

export interface DateRange {
  start: Date;
  stop: Date;
}

@Component({
  templateUrl: './timeline.component.html',
  styleUrl: './timeline.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TimelineDetail, WebappSdkModule],
  providers: [TimelineService],
})
export class TimelineComponent implements AfterViewInit, OnDestroy {
  private el = inject(ElementRef);
  private prefs = inject(Preferences);

  @ViewChild('container', { static: true })
  container: ElementRef;

  views$ = new BehaviorSubject<TimelineView[]>([]);
  view$ = new BehaviorSubject<TimelineView | null>(null);

  timeline: Timeline;
  private moveInterval?: number;

  viewportRange$ = new BehaviorSubject<DateRange | null>(null);

  private bands: Band[] = [];

  private subscriptions: Subscription[] = [];
  private changeSubscription: WebSocketCall<any, any>;

  private overlayRef: OverlayRef;
  private tooltipInstance: YaTooltip;

  constructor(
    title: Title,
    readonly yamcs: YamcsService,
    private dialog: MatDialog,
    private messageService: MessageService,
    private formatter: Formatter,
    readonly route: ActivatedRoute,
    private router: Router,
    private authService: AuthService,
    private synchronizer: Synchronizer,
    private configService: ConfigService,
    private overlay: Overlay,
    readonly timelineService: TimelineService,
  ) {
    title.setTitle('Timeline');

    /**
     * If our locally scoped service gives a refresh signal,
     * than refresh the chart
     */
    effect(() => {
      this.timelineService.refreshTrigger();
      this.refreshView();
    });

    this.setupTooltip();
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

    this.overlayRef = this.overlay.create({ positionStrategy });
    const tooltipPortal = new ComponentPortal(YaTooltip);
    const componentRef = this.overlayRef.attach(tooltipPortal);
    componentRef.setInput('html', true);
    this.tooltipInstance = componentRef.instance;
  }

  @HostListener('mouseleave')
  hideTooltip() {
    this.tooltipInstance.hide();
  }

  ngAfterViewInit() {
    const detailHeight = this.prefs.getNumber(PREF_DETAIL_HEIGHT, 300);
    this.el.nativeElement.style.setProperty(
      '--y-timeline-detail-height',
      `${detailHeight}px`,
    );

    const queryParams = this.route.snapshot.queryParamMap;
    const requestedView =
      queryParams.get('view') ?? this.prefs.getString(PREF_VIEW);

    this.updateViews().then((views) => {
      if (!views) {
        return;
      }

      this.views$.next(views || []);

      // Respect the requested view (from query param)
      // But default to the first if unspecified.
      let view = null;
      for (const candidate of views || []) {
        if (requestedView === candidate.id) {
          view = candidate;
          break;
        }
      }
      if (!view) {
        view = views.length ? views[0] : null;
      }
      if (view) {
        this.switchView(view);
      }
    });

    this.timeline = new Timeline(this.container.nativeElement);
    this.timeline.leftSidebar = new DefaultSidebar(this.timeline);

    this.timeline.addViewportChangeListener((event) => {
      this.viewportRange$.next({
        start: new Date(event.start),
        stop: new Date(event.stop),
      });
    });
    this.viewportRange$.pipe(debounceTime(400)).forEach((range) => {
      this.refreshData();
      this.router.navigate([], {
        replaceUrl: true,
        relativeTo: this.route,
        queryParamsHandling: 'merge',
        queryParams: {
          start: range!.start.toISOString(),
          stop: range!.stop.toISOString(),
          date: undefined, // Unset if present (used in notifications)
        },
      });
    });
    // this.timeline.leftSidebar!.backgroundColor = '#fcfcfc';

    if (queryParams.get('start') && queryParams.get('stop')) {
      const start = utils.toDate(queryParams.get('start'));
      const stop = utils.toDate(queryParams.get('stop'));
      this.timeline.setViewRange(start.getTime(), stop.getTime());
    } else {
      // Show 1 hour
      let midTime = this.yamcs.getMissionTime();
      if (queryParams.has('date')) {
        midTime = utils.toDate(queryParams.get('date'));
        this.timeline.panTo(midTime.getTime(), false);
      }

      const start = addMinutes(midTime, -30);
      const stop = addMinutes(midTime, 30);
      this.timeline.setViewRange(start.getTime(), stop.getTime());
    }

    const locator = new TimeLocator(this.timeline);
    locator.time = this.yamcs.getMissionTime().getTime();

    this.subscriptions.push(
      this.synchronizer.sync(() => {
        const now = this.yamcs.getMissionTime().getTime();
        locator.time = now;
        this.onTick(now);
      }),
    );

    new MouseTracker(this.timeline);
    new HoveredDateAnnotation(this.timeline, this.formatter);

    this.refreshData();
    this.changeSubscription =
      this.yamcs.yamcsClient.createItemChangesSubscription(
        {
          instance: this.yamcs.instance!,
        },
        () => this.refreshView(),
      );
  }

  /**
   * Update views. Important to re-fetch data, because it's these
   * objects that are used in switchView.
   */
  updateViews() {
    return this.yamcs.yamcsClient
      .getTimelineViews(this.yamcs.instance!)
      .then((page) => {
        const views = page.views || [];
        this.views$.next(views);
        return views;
      })
      .catch((err) => this.messageService.showError(err));
  }

  private updateView(options: { name?: string; bands?: TimelineBand[] }) {
    const view = this.view$.value!;
    const request: UpdateTimelineViewRequest = {
      name: options.name ?? view.name,
      bands: (options.bands ?? view.bands ?? []).map(
        (band: TimelineBand) => band.id,
      ),
    };
    return this.timelineService.updateView(view.id, request);
  }

  refreshView() {
    const view = this.view$.value;
    if (view) {
      this.yamcs.yamcsClient
        .getTimelineView(this.yamcs.instance!, view.id)
        .then((updatedView) => this.switchView(updatedView))
        .catch((err) => this.messageService.showError(err));
    }
  }

  switchView(view: TimelineView | null) {
    // Remember view, so we open it by default when there is no
    // URL parameter.
    this.prefs.setString(PREF_VIEW, view?.id ?? null);

    // Update URL
    this.router.navigate([], {
      replaceUrl: true,
      relativeTo: this.route,
      queryParams: {
        view: view?.id || null,
      },
      queryParamsHandling: 'merge',
    });

    this.view$.next(view);
    for (const line of this.timeline.getBands()) {
      this.timeline.removeChild(line);
    }
    if (view) {
      this.bands.length = 0;
      for (const bandInfo of view.bands || []) {
        if (bandInfo.type === 'TIME_RULER') {
          const band = new TimeRuler(this, bandInfo);
          this.bands.push(band);
          this.installBandListeners(band);
        } else if (bandInfo.type === 'ITEM_BAND') {
          const band = new ItemBand(
            this,
            bandInfo,
            this.formatter,
            this.tooltipInstance,
            this.authService,
          );
          this.bands.push(band);
          this.installBandListeners(band);
        } else if (bandInfo.type === 'SPACER') {
          const band = new Banner(this.timeline);
          this.bands.push(band);
        } else if (bandInfo.type === 'COMMAND_BAND') {
          const band = new CommandBand(this, bandInfo);
          this.bands.push(band);
          this.installBandListeners(band);
        } else if (bandInfo.type === 'PARAMETER_PLOT') {
          const band = new ParameterPlot(
            this,
            bandInfo,
            this.yamcs,
            this.synchronizer,
            this.configService,
            this.overlay,
          );
          this.bands.push(band);
          this.installBandListeners(band);
        } else if (bandInfo.type === 'PARAMETER_STATES') {
          const band = new ParameterStateBand(
            this,
            bandInfo,
            this.yamcs,
            this.synchronizer,
            this.formatter,
            this.configService,
            this.overlay,
          );
          this.bands.push(band);
          this.installBandListeners(band);
        }
      }
      this.refreshData();
    }
  }

  /**
   * Some band types (plot in particular) may stop updating
   * if the tab is inactive for a while, as part of a kind
   * of energy saving mode.
   *
   * When the tab becomes active again, make sure to undo
   * any weird visual artifacts coming from this.
   */
  @HostListener('document:visibilitychange')
  onVisibilityChange() {
    if (!document.hidden) {
      this.refreshView();
    }
  }

  private installBandListeners(band: Band) {
    if (this.mayControlTimeline()) {
      band.addHeaderClickListener((evt) => {
        const band = evt.band.data.band;
        this.timelineService.openEditBandDialog(band);
      });
    }
  }

  private onTick(now: number) {
    for (const band of this.bands) {
      if (band instanceof BandBase) {
        band.onTick(now);
      }
    }
  }

  refreshData() {
    const queriedBands: DefaultItemBand[] = [];
    const promises = [];

    const visibleRange: TimeRange = {
      start: this.timeline.start,
      stop: this.timeline.stop,
    };

    // Load beyond the edges (for pan purposes)
    const padding = (visibleRange.stop - visibleRange.start) / 2;
    const loadStart = visibleRange.start - padding;
    const loadStop = visibleRange.stop + padding;
    const loadRange: TimeRange = { start: loadStart, stop: loadStop };

    for (const band of this.bands) {
      if (band instanceof BandBase) {
        band.refreshData(loadRange, visibleRange);
      } else if (band instanceof CommandBand) {
        queriedBands.push(band);
        promises.push(
          this.yamcs.yamcsClient.getTimelineItems(this.yamcs.instance!, {
            source: 'commands',
            band: band.data.band.id !== '_' ? band.data.band.id : undefined,
            start: new Date(loadStart).toISOString(),
            stop: new Date(loadStop).toISOString(),
          }),
        );
      } else if (band instanceof ParameterPlot) {
        band.refreshData();
      } else if (band instanceof ParameterStateBand) {
        band.refreshData();
      }
    }
    if (promises.length) {
      Promise.all(promises)
        .then((responses) => {
          for (let i = 0; i < responses.length; i++) {
            const band = queriedBands[i];
            this.populateItems(band, responses[i].items || []);
          }
        })
        .catch((err) => this.messageService.showError(err));
    }
  }

  private populateItems(band: DefaultItemBand, itemInfos: TimelineItem[]) {
    const items: Item[] = [];

    for (const itemInfo of itemInfos) {
      const start = utils.toDate(itemInfo.start).getTime();
      const duration = utils.convertProtoDurationToMillis(itemInfo.duration);
      const item: Item = {
        id: itemInfo.id,
        start,
        stop: duration ? start + duration : undefined,
        label: itemInfo.name,
        data: { item: itemInfo },
      };
      const { properties } = itemInfo;
      if (properties) {
        if ('backgroundColor' in properties) {
          item.background = properties.backgroundColor;
        }
        if ('borderColor' in properties) {
          item.borderColor = properties.borderColor;
        }
        if ('borderWidth' in properties) {
          item.borderWidth = Number(properties.borderWidth);
        }
        if ('cornerRadius' in properties) {
          item.cornerRadius = Number(properties.cornerRadius);
        }
        if ('marginLeft' in properties) {
          item.paddingLeft = Number(properties.marginLeft);
        }
        if ('textColor' in properties) {
          item.textColor = properties.textColor;
        }
        if ('textSize' in properties) {
          item.textSize = Number(properties.textSize);
        }
      }
      items.push(item);
    }
    band.items = items;
  }

  openCreateViewDialog() {
    this.timelineService.openCreateViewDialog();
  }

  openCreateEventDialog() {
    this.dialog
      .open(CreateEventDialogComponent, {
        width: '600px',
        panelClass: 'dialog-force-no-scrollbar',
        data: { type: 'EVENT' },
      })
      .afterClosed()
      .subscribe(() => this.refreshData());
  }

  openCreateTaskDialog() {
    this.dialog
      .open(CreateTaskDialogComponent, {
        width: '600px',
        panelClass: 'dialog-force-no-scrollbar',
      })
      .afterClosed()
      .subscribe(() => this.refreshData());
  }

  async openCreateBandDialog() {
    const view = this.view$.value!;
    const band = await this.timelineService.openCreateBandDialog({
      title: 'Create new',
      submitLabel: 'Add to view',
    });
    if (band) {
      const bands = [...(view.bands || []), band];
      this.updateView({ bands });
    }
  }

  openAddBandDialog() {
    const view = this.view$.value!;
    this.dialog
      .open(SelectBandDialogComponent, {
        width: '800px',
        panelClass: ['no-padding-dialog'],
      })
      .afterClosed()
      .subscribe((band) => {
        if (band) {
          const bands = [...(view.bands || []), band];
          this.updateView({ bands });
        }
      });
  }

  toggleMove(x: number) {
    this.timeline.panBy(x);
    window.clearInterval(this.moveInterval);
    this.moveInterval = window.setInterval(() => this.timeline.panBy(x), 50);
  }

  untoggleMove() {
    window.clearInterval(this.moveInterval);
    this.moveInterval = undefined;
  }

  pageLeft() {
    const { start, stop } = this.timeline;
    const x = this.timeline.distanceBetween(start, stop);
    this.timeline.panBy(-x);
  }

  pageRight() {
    const { start, stop } = this.timeline;
    const x = this.timeline.distanceBetween(start, stop);
    this.timeline.panBy(x);
  }

  zoomIn() {
    this.timeline.zoomIn();
  }

  zoomOut() {
    this.timeline.zoomOut();
  }

  show3Hours() {
    const midTime =
      this.timeline.start + (this.timeline.stop - this.timeline.start) / 2;
    const start = addHours(midTime, -1.5);
    const stop = addHours(midTime, 1.5);
    this.timeline.setViewRange(start.getTime(), stop.getTime());
  }

  show1Hour() {
    const midTime =
      this.timeline.start + (this.timeline.stop - this.timeline.start) / 2;
    const start = addMinutes(midTime, -30);
    const stop = addMinutes(midTime, 30);
    this.timeline.setViewRange(start.getTime(), stop.getTime());
  }

  show10Minutes() {
    const midTime =
      this.timeline.start + (this.timeline.stop - this.timeline.start) / 2;
    const start = addMinutes(midTime, -5);
    const stop = addMinutes(midTime, 5);
    this.timeline.setViewRange(start.getTime(), stop.getTime());
  }

  jumpToToday() {
    const dt = this.yamcs.getMissionTime();
    dt.setUTCHours(0, 0, 0, 0);
    const start = dt.getTime();
    dt.setUTCDate(dt.getUTCDate() + 1);
    const stop = dt.getTime();
    this.timeline.setViewRange(start, stop);
  }

  jumpToNow() {
    const missionTime = this.yamcs.getMissionTime();
    this.timeline.panTo(missionTime.getTime());
  }

  openJumpToDialog() {
    const currentDate = this.timeline.center;
    this.dialog
      .open(JumpToDialogComponent, {
        width: '400px',
        data: { date: new Date(currentDate) },
      })
      .afterClosed()
      .subscribe((result) => {
        if (result) {
          this.timeline.panTo(result.date.getTime());
        }
      });
  }

  saveSnapshot() {
    const a = document.createElement('a');
    try {
      a.href = this.timeline.toDataURL();
      a.download = 'timeline_' + this.view$.value!.name + '_export.png';
      document.body.appendChild(a);
      a.click();
    } finally {
      document.body.removeChild(a);
    }
  }

  mayControlTimeline() {
    return this.authService.getUser()!.hasSystemPrivilege('ControlTimeline');
  }

  onResizerMouseDown(mouseEvent: MouseEvent) {
    mouseEvent.preventDefault();

    const minHeight = 28;
    const maxHeight = window.innerHeight * 0.75;

    const onMouseMove = (e: MouseEvent) => {
      let newHeight = window.innerHeight - e.clientY;
      newHeight = Math.min(maxHeight, Math.max(minHeight, newHeight));

      // Update the CSS variable on the root (or a specific parent element)
      this.el.nativeElement.style.setProperty(
        '--y-timeline-detail-height',
        `${newHeight}px`,
      );
      this.prefs.setNumber(PREF_DETAIL_HEIGHT, newHeight);
    };

    const onMouseUp = () => {
      document.removeEventListener('mousemove', onMouseMove);
      document.removeEventListener('mouseup', onMouseUp);
    };

    document.addEventListener('mousemove', onMouseMove);
    document.addEventListener('mouseup', onMouseUp);
  }

  ngOnDestroy() {
    this.overlayRef.dispose();
    this.changeSubscription.cancel();
    this.subscriptions.forEach((subscription) => subscription.unsubscribe());
    this.timeline?.disconnect();
  }
}
