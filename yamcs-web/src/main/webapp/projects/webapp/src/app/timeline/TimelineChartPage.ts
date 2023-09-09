import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, OnDestroy, ViewChild } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { Band, Banner, ItemBand as DefaultItemBand, Item, MouseTracker, TimeLocator, Timeline } from '@fqqb/timeline';
import { MessageService, Synchronizer, TimelineItem, TimelineView, YamcsService, utils } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { AuthService } from '../core/services/AuthService';
import { CommandBand } from './commandBand/CommandBand';
import { CreateItemDialog } from './dialogs/CreateItemDialog';
import { EditBandDialog } from './dialogs/EditBandDialog';
import { EditViewDialog } from './dialogs/EditViewDialog';
import { JumpToDialog } from './dialogs/JumpToDialog';
import { ItemBand } from './itemBand/ItemBand';
import { TimeRuler } from './timeRuler/TimeRuler';

interface DateRange {
  start: Date;
  stop: Date;
}


@Component({
  templateUrl: './TimelineChartPage.html',
  styleUrls: ['./TimelineChartPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TimelineChartPage implements AfterViewInit, OnDestroy {

  @ViewChild('container', { static: true })
  container: ElementRef;

  views$ = new BehaviorSubject<TimelineView[]>([]);
  view$ = new BehaviorSubject<TimelineView | null>(null);

  timeline: Timeline;
  private moveInterval?: number;

  viewportRange$ = new BehaviorSubject<DateRange | null>(null);

  private bands: Band[] = [];

  private subscriptions: Subscription[] = [];

  constructor(
    title: Title,
    readonly yamcs: YamcsService,
    private dialog: MatDialog,
    private messageService: MessageService,
    readonly route: ActivatedRoute,
    private router: Router,
    private authService: AuthService,
    private synchronizer: Synchronizer,
  ) {
    title.setTitle('Timeline Chart');
  }

  ngAfterViewInit() {
    const queryParams = this.route.snapshot.queryParamMap;
    const requestedView = queryParams.get('view');

    this.yamcs.yamcsClient.getTimelineViews(this.yamcs.instance!).then(page => {
      this.views$.next(page.views || []);

      // Respect the requested view (from query param)
      // But default to the first if unspecified.
      let view = null;
      for (const candidate of (page.views || [])) {
        if (requestedView === candidate.id) {
          view = candidate;
          break;
        }
      }
      if (!view && !requestedView) {
        view = page.views?.length ? page.views[0] : null;
      }
      if (view) {
        this.switchView(view);
      }
    });

    this.timeline = new Timeline(this.container.nativeElement);

    this.timeline.addViewportChangeListener(event => {
      this.viewportRange$.next({
        start: new Date(event.start),
        stop: new Date(event.stop),
      });
    });
    this.viewportRange$.pipe(
      debounceTime(400),
    ).forEach(range => {
      this.refreshData();
      this.router.navigate([], {
        replaceUrl: true,
        relativeTo: this.route,
        queryParamsHandling: 'merge',
        queryParams: {
          start: range!.start.toISOString(),
          stop: range!.stop.toISOString(),
        }
      });
    });
    // this.timeline.sidebar!.backgroundColor = '#fcfcfc';

    if (queryParams.get('start') && queryParams.get('stop')) {
      const start = utils.toDate(queryParams.get('start'));
      const stop = utils.toDate(queryParams.get('stop'));
      this.timeline.setViewRange(start.getTime(), stop.getTime());
    } else {
      // Show Today
      const start = this.yamcs.getMissionTime();
      start.setUTCHours(0, 0, 0, 0);
      const stop = new Date(start.getTime());
      stop.setUTCDate(start.getUTCDate() + 1);
      this.timeline.setViewRange(start.getTime(), stop.getTime());
    }

    const locator = new TimeLocator(this.timeline);
    locator.time = this.yamcs.getMissionTime().getTime();

    this.subscriptions.push(
      this.synchronizer.sync(() => {
        locator.time = this.yamcs.getMissionTime().getTime();
      })
    );

    new MouseTracker(this.timeline);

    this.refreshData();
  }

  refreshView() {
    const view = this.view$.value;
    if (view) {
      this.yamcs.yamcsClient.getTimelineView(this.yamcs.instance!, view.id)
        .then(updatedView => this.switchView(updatedView))
        .catch(err => this.messageService.showError(err));
    }
  }

  switchView(view: TimelineView | null) {
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
      for (const bandInfo of (view.bands || [])) {
        if (bandInfo.type === 'TIME_RULER') {
          const band = new TimeRuler(this, bandInfo);
          this.bands.push(band);
          this.installBandListeners(band);
        } else if (bandInfo.type === 'ITEM_BAND') {
          const band = new ItemBand(this, bandInfo, this.dialog);
          this.bands.push(band);
          this.installBandListeners(band);
        } else if (bandInfo.type === 'SPACER') {
          const band = new Banner(this.timeline);
          this.bands.push(band);
        } else if (bandInfo.type === 'COMMAND_BAND') {
          const band = new CommandBand(this, bandInfo);
          this.bands.push(band);
          this.installBandListeners(band);
        }
      }
      this.refreshData();
    }
  }

  private installBandListeners(band: Band) {
    band.addHeaderClickListener(evt => {
      const band = evt.band.data.band;
      const dialogRef = this.dialog.open(EditBandDialog, {
        width: '70%',
        height: '100%',
        autoFocus: false,
        position: {
          right: '0',
        },
        panelClass: 'dialog-full-size',
        data: { band }
      });
      dialogRef.afterClosed().subscribe(updatedBand => {
        if (updatedBand) {
          this.refreshView();
        }
      });
    });
  }

  refreshData() {
    const queriedBands: DefaultItemBand[] = [];
    const promises = [];

    // Load beyond the edges (for pan purposes)
    const viewportRange = this.timeline.stop - this.timeline.start;
    const loadStart = this.timeline.start - viewportRange;
    const loadStop = this.timeline.stop + viewportRange;

    for (const band of this.bands) {
      if (band instanceof ItemBand) {
        queriedBands.push(band);
        promises.push(this.yamcs.yamcsClient.getTimelineItems(this.yamcs.instance!, {
          source: 'rdb',
          band: band.data.band.id,
          start: new Date(loadStart).toISOString(),
          stop: new Date(loadStop).toISOString(),
        }));
      } else if (band instanceof CommandBand) {
        queriedBands.push(band);
        promises.push(this.yamcs.yamcsClient.getTimelineItems(this.yamcs.instance!, {
          source: 'commands',
          band: band.data.band.id,
          start: new Date(loadStart).toISOString(),
          stop: new Date(loadStop).toISOString(),
        }));
      }
    }
    if (promises.length) {
      Promise.all(promises).then(responses => {
        for (let i = 0; i < responses.length; i++) {
          const band = queriedBands[i];
          this.populateItems(band, responses[i].items || []);
        }
      }).catch(err => this.messageService.showError(err));
    }
  }

  private populateItems(band: DefaultItemBand, itemInfos: TimelineItem[]) {
    const items: Item[] = [];
    for (const itemInfo of itemInfos) {
      const start = utils.toDate(itemInfo.start).getTime();
      const duration = utils.convertProtoDurationToMillis(itemInfo.duration);
      const item: Item = {
        start,
        stop: duration ? start + duration : undefined,
        label: itemInfo.name,
        data: { item: itemInfo },
      };
      items.push(item);
    }
    band.items = items;
  }

  openCreateItemDialog() {
    this.dialog.open(CreateItemDialog, {
      width: '600px',
    }).afterClosed().subscribe(() => this.refreshData());
  }

  openEditViewDialog(view: TimelineView) {
    this.dialog.open(EditViewDialog, {
      width: '70%',
      height: '100%',
      panelClass: 'dialog-full-size',
      autoFocus: false,
      position: {
        right: '0',
      },
      data: { view }
    }).afterClosed().subscribe(updatedView => {
      if (updatedView) {
        this.switchView(updatedView);
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
    this.dialog.open(JumpToDialog, {
      width: '400px',
      data: { date: new Date(currentDate) },
    }).afterClosed().subscribe(result => {
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

  ngOnDestroy() {
    this.subscriptions.forEach(subscription => subscription.unsubscribe());
    this.timeline.disconnect();
  }
}
