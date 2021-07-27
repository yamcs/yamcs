import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, OnDestroy, ViewChild } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { AbsoluteTimeAxis, Event, EventLine, Line, MouseTracker, Timeline, TimeLocator } from '@fqqb/timeline';
import { BehaviorSubject } from 'rxjs';
import { TimelineItem, TimelineView } from '../client/types/timeline';
import { AuthService } from '../core/services/AuthService';
import { MessageService } from '../core/services/MessageService';
import { YamcsService } from '../core/services/YamcsService';
import * as utils from '../shared/utils';
import { CreateItemDialog } from './dialogs/CreateItemDialog';
import { EditBandDialog } from './dialogs/EditBandDialog';
import { EditItemDialog } from './dialogs/EditItemDialog';
import { EditViewDialog } from './dialogs/EditViewDialog';
import { JumpToDialog } from './dialogs/JumpToDialog';
import { addDefaultItemBandProperties } from './itemBand/ItemBandStyles';
import { addDefaultSpacerProperties } from './spacer/SpacerStyles';


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

  private timeline: Timeline;
  private moveInterval?: number;

  private lines: Line<any>[] = [];
  private idByLine = new Map<Line<any>, string>();

  constructor(
    title: Title,
    readonly yamcs: YamcsService,
    private dialog: MatDialog,
    private messageService: MessageService,
    readonly route: ActivatedRoute,
    private router: Router,
    private authService: AuthService,
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
    // this.timeline.sidebar!.backgroundColor = '#fcfcfc';

    // Show Today
    const start = this.yamcs.getMissionTime();
    start.setUTCHours(0, 0, 0, 0);
    const stop = new Date(start.getTime());
    stop.setUTCDate(start.getUTCDate() + 1);
    this.timeline.setBounds(start.getTime(), stop.getTime());

    const locator = new TimeLocator(this.timeline, () => this.yamcs.getMissionTime().getTime());
    locator.knobColor = 'salmon';

    new MouseTracker(this.timeline);

    this.timeline.addEventListener('headerclick', evt => {
      const id = this.idByLine.get(evt.line);
      if (id) {
        const band = this.view$.value!.bands!.filter(b => b.id === id)[0];
        const dialogRef = this.dialog.open(EditBandDialog, {
          width: '70%',
          height: '100%',
          autoFocus: false,
          position: {
            right: '0',
          },
          data: { band }
        });
        dialogRef.afterClosed().subscribe(updatedBand => {
          if (updatedBand) {
            this.refreshView();
          }
        });
      }
    });

    this.timeline.addEventListener('eventclick', evt => {
      const dialogRef = this.dialog.open(EditItemDialog, {
        width: '600px',
        data: { item: evt.event.data.item }
      });
      dialogRef.afterClosed().subscribe(() => this.refreshData());
    });

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
    for (const line of this.timeline.getLines()) {
      this.timeline.removeLine(line);
    }
    if (view) {
      for (const band of (view.bands || [])) {
        if (band.type === 'TIME_RULER') {
          const axis = new AbsoluteTimeAxis(this.timeline);
          axis.label = band.name;
          axis.timezone = band.properties!.timezone;
          // axis.frozen = true;
          this.lines.push(axis);
          this.idByLine.set(axis, band.id);
        } else if (band.type === 'ITEM_BAND') {
          const eventLine = new EventLine(this.timeline);
          eventLine.label = band.name;

          const properties = addDefaultItemBandProperties(band.properties || {});
          eventLine.eventColor = properties.itemBackgroundColor;
          eventLine.borderColor = properties.itemBorderColor;
          eventLine.borderWidth = properties.itemBorderWidth;
          eventLine.cornerRadius = properties.itemCornerRadius;
          eventLine.eventHeight = properties.itemHeight;
          eventLine.eventMarginLeft = properties.itemMarginLeft;
          eventLine.textColor = properties.itemTextColor;
          eventLine.textOverflow = properties.itemTextOverflow;
          eventLine.textSize = properties.itemTextSize;
          eventLine.marginBottom = properties.marginBottom;
          eventLine.marginTop = properties.marginTop;
          eventLine.wrap = properties.multiline;
          eventLine.spaceBetween = properties.spaceBetweenItems;
          eventLine.lineSpacing = properties.spaceBetweenLines;

          this.lines.push(eventLine);
          this.idByLine.set(eventLine, band.id);
        } else if (band.type === 'SPACER') {
          const spacer = new EventLine(this.timeline);
          spacer.label = band.name;

          const properties = addDefaultSpacerProperties(band.properties || {});
          spacer.eventHeight = properties.height;
          spacer.marginTop = 0;
          spacer.marginBottom = 0;

          this.lines.push(spacer);
          this.idByLine.set(spacer, band.id);
        }
      }
      this.refreshData();
    }
  }

  refreshData() {
    const queriedLines: EventLine[] = [];
    const promises = [];
    for (const line of this.lines) {
      const band = this.getBandForLine(line);
      if (band && band.type === 'ITEM_BAND') {
        queriedLines.push(line as EventLine);
        promises.push(this.yamcs.yamcsClient.getTimelineItems(this.yamcs.instance!, {
          type: 'EVENT',
        }));
      }
    }
    if (promises.length) {
      Promise.all(promises).then(responses => {
        for (let i = 0; i < responses.length; i++) {
          const line = queriedLines[i];
          this.populateEvents(line, responses[i].items || []);
        }
      }).catch(err => this.messageService.showError(err));
    }
  }

  private populateEvents(line: EventLine, items: TimelineItem[]) {
    const data: Event[] = [];
    for (const item of items) {
      const start = utils.toDate(item.start).getTime();
      const event: Event = {
        start,
        stop: start + utils.convertProtoDurationToMillis(item.duration),
        title: item.name,
        data: { item },
      };
      data.push(event);
    }
    line.data = data;
  }

  openCreateItemDialog() {
    const dialogRef = this.dialog.open(CreateItemDialog, {
      width: '600px',
    });
    dialogRef.afterClosed().subscribe(() => this.refreshData());
  }

  openEditViewDialog(view: TimelineView) {
    const dialogRef = this.dialog.open(EditViewDialog, {
      width: '70%',
      height: '100%',
      autoFocus: false,
      position: {
        right: '0',
      },
      data: { view }
    });
    dialogRef.afterClosed().subscribe(updatedView => {
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
    this.timeline.setBounds(start, stop);
  }

  jumpToNow() {
    this.timeline.panTo(new Date().getTime());
  }

  openJumpToDialog() {
    const currentDate = this.timeline.center;
    const dialogRef = this.dialog.open(JumpToDialog, {
      width: '400px',
      data: { date: new Date(currentDate) },
    });
    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.timeline.panTo(result.date.getTime());
      }
    });
  }

  mayControlTimeline() {
    return this.authService.getUser()!.hasSystemPrivilege('ControlTimeline');
  }

  private getBandForLine(line: Line<any>) {
    const id = this.idByLine.get(line);
    if (id) {
      for (const band of this.view$.value?.bands || []) {
        if (band.id === id) {
          return band;
        }
      }
    }
  }

  ngOnDestroy() {
    this.timeline.disconnect();
  }
}
