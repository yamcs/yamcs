import { Overlay } from '@angular/cdk/overlay';
import { ComponentPortal } from '@angular/cdk/portal';
import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, HostListener, OnDestroy, ViewChild } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Params, Router } from '@angular/router';
import { Event, EventBand, MouseTracker, Timeline, TimeLocator, TimeRuler, Tool } from '@fqqb/timeline';
import { BehaviorSubject } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { IndexGroup } from '../client';
import { MessageService } from '../core/services/MessageService';
import { YamcsService } from '../core/services/YamcsService';
import { DateTimePipe } from '../shared/pipes/DateTimePipe';
import { StartReplayDialog } from '../shared/template/StartReplayDialog';
import * as utils from '../shared/utils';
import { DownloadDumpDialog } from './DownloadDumpDialog';
import { JumpToDialog } from './JumpToDialog';
import { TimelineTooltip } from './TimelineTooltip';

interface DateRange {
  start: Date;
  stop: Date;
}

@Component({
  templateUrl: './ArchiveBrowserPage.html',
  styleUrls: ['./ArchiveBrowserPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ArchiveBrowserPage implements AfterViewInit, OnDestroy {

  completenessBg = 'orange';
  completenessFg = 'rgb(173, 94, 0)';
  packetsBg = 'palegoldenrod';
  packetsFg = '#555';
  parametersBg = 'navajowhite';
  parametersFg = '#1c4b8b';
  commandsBg = '#ffcc00';
  commandsFg = '#1c4b8b';
  eventsBg = '#ffff66';
  eventsFg = '#1c4b8b';

  legendOptions = [
    { id: 'packets', name: 'Packets', bg: this.packetsBg, fg: this.packetsFg, checked: true },
    { id: 'parameters', name: 'Parameters', bg: this.parametersBg, fg: this.parametersFg, checked: true },
    { id: 'commands', name: 'Commands', bg: this.commandsBg, fg: this.commandsFg, checked: false },
    { id: 'events', name: 'Events', bg: this.eventsBg, fg: this.eventsFg, checked: false },
  ];

  @ViewChild('container', { static: true })
  container: ElementRef;

  filterForm: FormGroup;

  private timeline: Timeline;
  private moveInterval?: number;

  rangeSelection$ = new BehaviorSubject<DateRange | null>(null);
  viewportRange$ = new BehaviorSubject<DateRange | null>(null);
  tool$ = new BehaviorSubject<Tool>('hand');

  private tooltipInstance: TimelineTooltip;

  private packetNames: string[] = [];

  constructor(
    title: Title,
    readonly yamcs: YamcsService,
    private route: ActivatedRoute,
    private router: Router,
    private overlay: Overlay,
    private dialog: MatDialog,
    private snackBar: MatSnackBar,
    private dateTimePipe: DateTimePipe,
    private messageService: MessageService,
  ) {
    title.setTitle('Archive Browser');

    const capabilities = yamcs.connectionInfo$.value?.instance?.capabilities || [];
    if (capabilities.indexOf('ccsds-completeness') !== -1) {
      this.legendOptions = [
        { id: 'completeness', name: 'Completeness', bg: this.completenessBg, fg: this.completenessFg, checked: true },
        ... this.legendOptions,
      ];
    }

    this.filterForm = new FormGroup({});
    const queryParams = this.route.snapshot.queryParamMap;
    for (const option of this.legendOptions) {
      let checked = option.checked;
      if (queryParams.has(option.id)) {
        checked = queryParams.get(option.id) === 'true';
      }
      this.filterForm.addControl(option.id, new FormControl(checked));
    }

    const bodyRef = new ElementRef(document.body);
    const positionStrategy = this.overlay.position().flexibleConnectedTo(bodyRef)
      .withPositions([{
        originX: 'start',
        originY: 'top',
        overlayX: 'start',
        overlayY: 'top',
      }]).withPush(false);

    const overlayRef = this.overlay.create({ positionStrategy });
    const tooltipPortal = new ComponentPortal(TimelineTooltip);
    this.tooltipInstance = overlayRef.attach(tooltipPortal).instance;
  }

  @HostListener('mouseleave')
  hideTooltip() {
    this.tooltipInstance.hide();
  }

  ngAfterViewInit() {
    // Fetch archive packets to ensure we can always show bands
    // even if there's no data for the visible range
    this.yamcs.yamcsClient.getPacketNames(this.yamcs.instance!).then(packetNames => {
      this.packetNames = packetNames;
      this.initializeTimeline();
    });
  }

  private initializeTimeline() {
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
      const legendParams: Params = {};
      for (const option of this.legendOptions) {
        legendParams[option.id] = this.filterForm.value[option.id];
      }
      this.router.navigate([], {
        replaceUrl: true,
        relativeTo: this.route,
        queryParamsHandling: 'merge',
        queryParams: {
          start: new Date(range!.start).toISOString(),
          stop: new Date(range!.stop).toISOString(),
          ...legendParams,
        }
      });
    });

    const queryParams = this.route.snapshot.queryParamMap;
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

    const locator = new TimeLocator(this.timeline, () => this.yamcs.getMissionTime().getTime());
    locator.knobColor = 'salmon';

    new MouseTracker(this.timeline);
    const axis = new TimeRuler(this.timeline);
    axis.label = 'UTC';
    axis.timezone = 'UTC';
    axis.frozen = true;
    axis.fullHeight = true;

    this.timeline.addEventClickListener(clickEvent => {
      const { start, stop } = clickEvent.event;
      if (start && stop) {
        this.timeline.setSelection(start, stop);
      } else {
        this.timeline.clearSelection();
      }
    });
    this.timeline.addViewportSelectionListener(evt => {
      if (evt.selection) {
        this.rangeSelection$.next({
          start: new Date(evt.selection.start),
          stop: new Date(evt.selection.stop),
        });
      } else {
        this.rangeSelection$.next(null);
      }
    });
    this.timeline.addEventMouseMoveListener(evt => {
      const { start, stop, data } = evt.event;
      let ttText = data.name + '<br>';
      ttText += `Start: ${this.dateTimePipe.transform(new Date(start))}<br>`;
      ttText += `Stop:&nbsp; ${this.dateTimePipe.transform(new Date(stop!))}<br>`;
      if (data.count >= 0) {
        const sec = (stop! - start) / 1000;
        ttText += `Count: ${data.count}`;
        if (data.count > 1) {
          ttText += ` (${(data.count / sec).toFixed(3)} Hz)`;
        }
      } else if (data.description) {
        ttText += data.description;
      }
      this.tooltipInstance.show(ttText, evt.clientX, evt.clientY);
    });
    this.timeline.addEventMouseOutListener(evt => {
      this.tooltipInstance.hide();
    });
  }

  fitAll() {
    const promises = [];
    promises.push(this.yamcs.yamcsClient.getPackets(this.yamcs.instance!, {
      limit: 1,
      order: 'asc',
    }));
    promises.push(this.yamcs.yamcsClient.getPackets(this.yamcs.instance!, {
      limit: 1,
      order: 'desc',
    }));
    Promise.all(promises).then(res => {
      const startPromise = res[0];
      const stopPromise = res[1];
      if (startPromise.packet?.length && stopPromise.packet?.length) {
        const start = utils.toDate(startPromise.packet[0].generationTime);
        const stop = utils.toDate(stopPromise.packet[0].generationTime);
        this.timeline.setViewRange(start.getTime(), stop.getTime());
      }
    }).catch(err => this.messageService.showError(err));
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

  setTool(tool: Tool) {
    this.tool$.next(tool);
    this.timeline.setActiveTool(tool);
  }

  replayRange() {
    const currentRange = this.rangeSelection$.value;
    if (currentRange) {
      const dialogRef = this.dialog.open(StartReplayDialog, {
        width: '400px',
        data: {
          start: currentRange.start,
          stop: currentRange.stop,
        },
      });
      dialogRef.afterClosed().subscribe(result => {
        if (result) {
          this.snackBar.open(`Initializing replay ${result.name}...`, undefined, {
            horizontalPosition: 'end',
          });
          this.yamcs.yamcsClient.createProcessor(result).then(() => {
            this.yamcs.switchContext(this.yamcs.instance!, result.name);
            this.snackBar.open(`Joining replay ${result.name}`, undefined, {
              duration: 3000,
              horizontalPosition: 'end',
            });
          }).catch(err => {
            this.snackBar.open(`Failed to initialize replay`, undefined, {
              duration: 3000,
              horizontalPosition: 'end',
            });
          });
        }
      });
    }
  }

  downloadDump() {
    const currentRange = this.rangeSelection$.value;
    if (currentRange) {
      this.dialog.open(DownloadDumpDialog, {
        width: '400px',
        data: {
          start: currentRange.start,
          stop: currentRange.stop,
        },
      });
    }
  }

  updateLegend() {
    this.refreshData();
  }

  refreshData() {
    // Load beyond the edges (for pan purposes)
    const viewportRange = this.timeline.stop - this.timeline.start;
    const start = new Date(this.timeline.start - viewportRange).toISOString();
    const stop = new Date(this.timeline.stop + viewportRange).toISOString();

    let completenessPromise: Promise<IndexGroup[]> = Promise.resolve([]);
    if (this.filterForm.value['completeness']) {
      completenessPromise = this.yamcs.yamcsClient.getCompletenessIndex(
        this.yamcs.instance!, { start, stop, limit: 1000 });
    }

    let tmPromise: Promise<IndexGroup[]> = Promise.resolve([]);
    if (this.filterForm.value['packets']) {
      tmPromise = this.yamcs.yamcsClient.getPacketIndex(
        this.yamcs.instance!, { start, stop, limit: 1000 });
    }

    let parameterPromise: Promise<IndexGroup[]> = Promise.resolve([]);
    if (this.filterForm.value['parameters']) {
      parameterPromise = this.yamcs.yamcsClient.getParameterIndex(
        this.yamcs.instance!, { start, stop, limit: 1000 });
    }

    let commandPromise: Promise<IndexGroup[]> = Promise.resolve([]);
    if (this.filterForm.value['commands']) {
      commandPromise = this.yamcs.yamcsClient.getCommandIndex(
        this.yamcs.instance!, { start, stop, limit: 1000 });
    }

    let eventPromise: Promise<IndexGroup[]> = Promise.resolve([]);
    if (this.filterForm.value['events']) {
      eventPromise = this.yamcs.yamcsClient.getEventIndex(
        this.yamcs.instance!, { start, stop, limit: 1000 });
    }

    Promise.all([
      completenessPromise,
      tmPromise,
      parameterPromise,
      commandPromise,
      eventPromise,
    ]).then(responses => {
      const completenessGroups = responses[0];
      const tmGroups = responses[1];
      const parameterGroups = responses[2];
      const commandGroups = responses[3];
      const eventGroups = responses[4];

      for (const line of this.timeline.getBands()) {
        if (!(line instanceof TimeRuler)) {
          this.timeline.removeChild(line);
        }
      }
      for (let i = 0; i < completenessGroups.length; i++) {
        if (i === 0) {
          const spacer = new EventBand(this.timeline);
          spacer.label = 'Completeness';
          spacer.backgroundColor = this.timeline.backgroundEvenColor;
          spacer.eventHeight = 30;
          spacer.marginTop = 0;
          spacer.marginBottom = 0;
        }
        const group = completenessGroups[i];
        const events: Event[] = [];
        for (const entry of group.entry) {
          const start = utils.toDate(entry.start).getTime();
          const stop = utils.toDate(entry.stop).getTime();
          const event: Event = {
            start, stop, data: {
              name: group.id.name,
              count: entry.count,
            }
          };
          if (entry.count > 1) {
            const sec = (stop - start) / 1000;
            event.label = `${(entry.count / sec).toFixed(1)} Hz`;
          }
          events.push(event);
        }
        const line = new EventBand(this.timeline);
        line.label = group.id.name;
        line.borderWidth = 0;
        line.multiline = false;
        line.events = events;
        line.marginTop = 0;
        line.marginBottom = i === completenessGroups.length - 1 ? 30 : 0;
        line.eventColor = this.completenessBg;
        line.eventTextColor = this.completenessFg;
        line.eventBorderWidth = 0;
        line.eventCornerRadius = 0;
        line.eventTextOverflow = 'hide';
        line.backgroundColor = this.timeline.backgroundOddColor;
      }

      if (this.filterForm.value['packets']) {
        for (let i = 0; i < this.packetNames.length; i++) {
          if (i === 0) {
            const spacer = new EventBand(this.timeline);
            spacer.label = 'Packets';
            spacer.backgroundColor = this.timeline.backgroundEvenColor;
            spacer.eventHeight = 30;
            spacer.marginTop = 0;
            spacer.marginBottom = 0;
          }
          const packetName = this.packetNames[i];
          const events: Event[] = [];
          for (const group of tmGroups) {
            if (group.id.name !== packetName) {
              continue;
            }
            for (const entry of group.entry) {
              const start = utils.toDate(entry.start).getTime();
              const stop = utils.toDate(entry.stop).getTime();
              const event: Event = {
                start, stop, data: {
                  name: group.id.name,
                  count: entry.count,
                }
              };
              if (entry.count > 1) {
                const sec = (stop - start) / 1000;
                event.label = `${(entry.count / sec).toFixed(1)} Hz`;
              }
              events.push(event);
            }
          }
          const line = new EventBand(this.timeline);
          line.label = packetName;
          line.borderWidth = i === this.packetNames.length - 1 ? 1 : 0;
          line.multiline = false;
          line.events = events;
          line.marginTop = 0;
          line.marginBottom = i === this.packetNames.length - 1 ? 30 : 0;
          line.eventColor = this.packetsBg;
          line.eventTextColor = this.packetsFg;
          line.eventBorderWidth = 0;
          line.eventCornerRadius = 0;
          line.eventTextOverflow = 'hide';
          line.backgroundColor = this.timeline.backgroundOddColor;
        }
      }

      for (let i = 0; i < parameterGroups.length; i++) {
        if (i === 0) {
          const spacer = new EventBand(this.timeline);
          spacer.label = 'Parameters';
          spacer.backgroundColor = this.timeline.backgroundEvenColor;
          spacer.eventHeight = 30;
          spacer.marginTop = 0;
          spacer.marginBottom = 0;
        }
        const group = parameterGroups[i];
        const events: Event[] = [];
        for (const entry of group.entry) {
          const start = utils.toDate(entry.start).getTime();
          const stop = utils.toDate(entry.stop).getTime();
          const event: Event = {
            start, stop, data: {
              name: group.id.name,
              count: entry.count,
            }
          };
          if (entry.count > 1) {
            const sec = (stop - start) / 1000;
            event.label = `${(entry.count / sec).toFixed(1)} Hz`;
          }
          events.push(event);
        }
        const line = new EventBand(this.timeline);
        line.label = group.id.name;
        line.borderWidth = 0;
        line.multiline = false;
        line.events = events;
        line.marginTop = 0;
        line.marginBottom = i === parameterGroups.length - 1 ? 30 : 0;
        line.eventColor = this.parametersBg;
        line.eventTextColor = this.parametersFg;
        line.eventBorderWidth = 0;
        line.eventCornerRadius = 0;
        line.eventTextOverflow = 'hide';
        line.backgroundColor = this.timeline.backgroundOddColor;
      }

      for (let i = 0; i < commandGroups.length; i++) {
        if (i === 0) {
          const spacer = new EventBand(this.timeline);
          spacer.label = 'Commands';
          spacer.backgroundColor = this.timeline.backgroundEvenColor;
          spacer.eventHeight = 30;
          spacer.marginTop = 0;
          spacer.marginBottom = 0;
        }
        const group = commandGroups[i];
        const events: Event[] = [];
        for (const entry of group.entry) {
          const start = utils.toDate(entry.start).getTime();
          const stop = utils.toDate(entry.stop).getTime();
          const event: Event = {
            start, stop, data: {
              name: group.id.name,
              count: entry.count,
            }
          };
          if (entry.count > 1) {
            const sec = (stop - start) / 1000;
            event.label = `${(entry.count / sec).toFixed(1)} Hz`;
          }
          events.push(event);
        }
        const line = new EventBand(this.timeline);
        line.label = group.id.name;
        line.borderWidth = 0;
        line.multiline = false;
        line.events = events;
        line.marginTop = 0;
        line.marginBottom = i === commandGroups.length - 1 ? 30 : 0;
        line.eventColor = this.commandsBg;
        line.eventTextColor = this.commandsFg;
        line.eventBorderWidth = 0;
        line.eventCornerRadius = 0;
        line.eventTextOverflow = 'hide';
        line.backgroundColor = this.timeline.backgroundOddColor;
      }

      for (let i = 0; i < eventGroups.length; i++) {
        if (i === 0) {
          const spacer = new EventBand(this.timeline);
          spacer.label = 'Events';
          spacer.backgroundColor = this.timeline.backgroundEvenColor;
          spacer.eventHeight = 30;
          spacer.marginTop = 0;
          spacer.marginBottom = 0;
        }
        const group = eventGroups[i];
        const events: Event[] = [];
        for (const entry of group.entry) {
          const start = utils.toDate(entry.start).getTime();
          const stop = utils.toDate(entry.stop).getTime();
          const event: Event = {
            start, stop, data: {
              name: group.id.name,
              count: entry.count,
            }
          };
          if (entry.count > 1) {
            const sec = (stop - start) / 1000;
            event.label = `${(entry.count / sec).toFixed(1)} Hz`;
          }
          events.push(event);
        }
        const line = new EventBand(this.timeline);
        line.label = group.id.name;
        line.borderWidth = 0;
        line.multiline = false;
        line.events = events;
        line.marginTop = 0;
        line.marginBottom = i === eventGroups.length - 1 ? 30 : 0;
        line.eventColor = this.eventsBg;
        line.eventTextColor = this.eventsFg;
        line.eventBorderWidth = 0;
        line.eventCornerRadius = 0;
        line.eventTextOverflow = 'hide';
        line.backgroundColor = this.timeline.backgroundOddColor;
      }
    });
  }

  ngOnDestroy() {
    this.timeline.disconnect();
  }
}
