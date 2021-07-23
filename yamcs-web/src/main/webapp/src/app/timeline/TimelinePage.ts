import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, OnDestroy, ViewChild } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Title } from '@angular/platform-browser';
import { AbsoluteTimeAxis, Event, EventLine, Line, MouseTracker, Timeline, TimeLocator } from '@fqqb/timeline';
import { TimelineItem } from '../client/types/timeline';
import { MessageService } from '../core/services/MessageService';
import { YamcsService } from '../core/services/YamcsService';
import * as utils from '../shared/utils';
import { CreateBandDialog } from './dialogs/CreateBandDialog';
import { CreateItemDialog } from './dialogs/CreateItemDialog';
import { EditItemDialog } from './dialogs/EditItemDialog';


@Component({
  templateUrl: './TimelinePage.html',
  styleUrls: ['./TimelinePage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TimelinePage implements AfterViewInit, OnDestroy {

  @ViewChild('container', { static: true })
  container: ElementRef;

  private timeline: Timeline;
  private moveInterval?: number;

  private bands: Line<any>[] = [];

  constructor(
    title: Title,
    readonly yamcs: YamcsService,
    private dialog: MatDialog,
    private messageService: MessageService,
  ) {
    title.setTitle('Timeline');
  }

  ngAfterViewInit() {
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

    this.yamcs.yamcsClient.getTimelineBands(this.yamcs.instance!).then(page => {
      for (const band of (page.bands || [])) {
        if (band.type === 'TIME') {
          const axis = new AbsoluteTimeAxis(this.timeline);
          axis.label = band.name;
          axis.timezone = band.extra!.timezone;
          console.log('tz is', axis.timezone);
          // axis.frozen = true;
          this.bands.push(axis);
        } else if (band.type === 'BANDEVENT') {
          const eventLine = new EventLine(this.timeline);
          eventLine.label = band.name;
          this.bands.push(eventLine);
        }
      }
      this.refreshData();
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

  refreshData() {
    const queriedBands: EventLine[] = [];
    const promises = [];
    for (const band of this.bands) {
      if (band instanceof EventLine) {
        queriedBands.push(band);
        promises.push(this.yamcs.yamcsClient.getTimelineItems(this.yamcs.instance!, {
          type: 'EVENT',
        }));
      }
    }
    if (promises.length) {
      Promise.all(promises).then(responses => {
        for (let i = 0; i < responses.length; i++) {
          const band = queriedBands[i];
          this.populateEvents(band, responses[i].items || []);
        }
      }).catch(err => this.messageService.showError(err));
    }
  }

  private populateEvents(band: EventLine, items: TimelineItem[]) {
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
    band.data = data;
  }

  openCreateItemDialog() {
    const dialogRef = this.dialog.open(CreateItemDialog, {
      width: '600px',
    });
    dialogRef.afterClosed().subscribe(() => this.refreshData());
  }

  openCreateBandDialog() {
    const dialogRef = this.dialog.open(CreateBandDialog, {
      width: '600px',
    });
    dialogRef.afterClosed().subscribe(() => this.refreshData());
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

  ngOnDestroy() {
    this.timeline.disconnect();
  }
}
