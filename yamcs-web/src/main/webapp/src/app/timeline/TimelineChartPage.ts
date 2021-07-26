import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, OnDestroy, ViewChild } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Title } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { AbsoluteTimeAxis, Event, EventLine, Line, MouseTracker, Timeline, TimeLocator } from '@fqqb/timeline';
import { TimelineItem } from '../client/types/timeline';
import { MessageService } from '../core/services/MessageService';
import { YamcsService } from '../core/services/YamcsService';
import * as utils from '../shared/utils';
import { CreateBandDialog } from './dialogs/CreateBandDialog';
import { CreateItemDialog } from './dialogs/CreateItemDialog';
import { EditItemDialog } from './dialogs/EditItemDialog';
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

  private timeline: Timeline;
  private moveInterval?: number;

  private bands: Line<any>[] = [];
  private idByBand = new Map<Line<any>, string>();

  constructor(
    title: Title,
    readonly yamcs: YamcsService,
    private dialog: MatDialog,
    private messageService: MessageService,
    private router: Router,
  ) {
    title.setTitle('Timeline Chart');
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
        if (band.type === 'TIME_RULER') {
          const axis = new AbsoluteTimeAxis(this.timeline);
          axis.label = band.name;
          axis.timezone = band.properties!.timezone;
          // axis.frozen = true;
          this.bands.push(axis);
          this.idByBand.set(axis, band.id);
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

          this.bands.push(eventLine);
          this.idByBand.set(eventLine, band.id);
        } else if (band.type === 'SPACER') {
          const spacer = new EventLine(this.timeline);
          spacer.label = band.name;

          const properties = addDefaultSpacerProperties(band.properties || {});
          spacer.eventHeight = properties.height;
          spacer.marginTop = 0;
          spacer.marginBottom = 0;

          this.bands.push(spacer);
          this.idByBand.set(spacer, band.id);
        }
      }
      this.refreshData();
    });

    this.timeline.addEventListener('headerclick', evt => {
      const id = this.idByBand.get(evt.line);
      if (id) {
        this.router.navigateByUrl(`/timeline/bands/${id}?c=${this.yamcs.context}`);
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

  ngOnDestroy() {
    this.timeline.disconnect();
  }
}
