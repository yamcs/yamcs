import { Overlay } from '@angular/cdk/overlay';
import { ComponentPortal } from '@angular/cdk/portal';
import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, HostListener, OnDestroy, ViewChild } from '@angular/core';
import { UntypedFormControl, UntypedFormGroup } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Params, Router } from '@angular/router';
import { MouseTracker, TimeLocator, TimeRuler, Timeline, Tool } from '@fqqb/timeline';
import { ArchiveRecord, EditReplayProcessorRequest, Formatter, MessageService, Processor, ProcessorSubscription, Synchronizer, WebappSdkModule, YamcsService, utils } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { InstancePageTemplateComponent } from '../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../shared/instance-toolbar/instance-toolbar.component';
import { StartReplayDialogComponent } from '../../shared/start-replay-dialog/start-replay-dialog.component';
import { DownloadDumpDialogComponent } from '../download-dump-dialog/download-dump-dialog.component';
import { JumpToDialogComponent } from '../jump-to-dialog/jump-to-dialog.component';
import { ArchiveRecordGroup } from '../model/ArchiveRecordGroup';
import { IndexGroupBand } from '../model/IndexGroupBand';
import { RGB } from '../model/RGB';
import { ReplayOverlay } from '../model/ReplayOverlay';
import { TitleBand } from '../model/TitleBand';
import { ModifyReplayDialogComponent } from '../modify-replay-dialog/modify-replay-dialog.component';
import { TimelineTooltipComponent } from '../timeline-tooltip/timeline-tooltip.component';

const COMMANDS_BG = new RGB(255, 204, 0);
const COMMANDS_FG = new RGB(28, 75, 139);
const COMPLETENESS_BG = new RGB(255, 165, 0);
const COMPLETENESS_FG = new RGB(173, 94, 0);
const EVENTS_BG = new RGB(255, 255, 102);
const EVENTS_FG = new RGB(28, 75, 139);
const PACKETS_BG = new RGB(238, 232, 170);
const PACKETS_FG = new RGB(85, 85, 85);
const PARAMETERS_BG = new RGB(255, 222, 173);
const PARAMETERS_FG = new RGB(28, 75, 139);

interface DateRange {
  start: Date;
  stop: Date;
}

function makeGradient(rgb: RGB) {
  return 'linear-gradient(to right, ' + rgb.toCssString(0.3) + ' 30%, ' + rgb.toCssString() + ')';
}

@Component({
  standalone: true,
  templateUrl: './archive-browser.component.html',
  styleUrl: './archive-browser.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    WebappSdkModule,
  ],
})
export class ArchiveBrowserComponent implements AfterViewInit, OnDestroy {

  legendOptions = [
    {
      id: 'packets',
      name: 'Packets',
      bg: makeGradient(PACKETS_BG),
      fg: PACKETS_FG.toCssString(),
      checked: true,
    },
    {
      id: 'parameters',
      name: 'Parameter Groups',
      bg: makeGradient(PARAMETERS_BG),
      fg: PARAMETERS_FG.toCssString(),
      checked: true,
    },
    {
      id: 'commands',
      name: 'Commands',
      bg: makeGradient(COMMANDS_BG),
      fg: COMMANDS_FG.toCssString(),
      checked: false,
    },
    {
      id: 'events',
      name: 'Events',
      bg: makeGradient(EVENTS_BG),
      fg: EVENTS_FG.toCssString(),
      checked: false,
    },
  ];

  @ViewChild('container', { static: true })
  container: ElementRef;

  filterForm: UntypedFormGroup;

  private timeline: Timeline;
  private moveInterval?: number;

  processor$ = new BehaviorSubject<Processor | null>(null);
  processorSubscription: ProcessorSubscription;

  rangeSelection$ = new BehaviorSubject<DateRange | null>(null);
  viewportRange$ = new BehaviorSubject<DateRange | null>(null);
  tool$ = new BehaviorSubject<Tool>('hand');

  private tooltipInstance: TimelineTooltipComponent;
  private packetNames: string[] = [];
  private subscriptions: Subscription[] = [];

  constructor(
    title: Title,
    readonly yamcs: YamcsService,
    private route: ActivatedRoute,
    private router: Router,
    private overlay: Overlay,
    private dialog: MatDialog,
    private snackBar: MatSnackBar,
    private messageService: MessageService,
    private synchronizer: Synchronizer,
    private formatter: Formatter,
  ) {
    title.setTitle('Archive browser');

    const capabilities = yamcs.connectionInfo$.value?.instance?.capabilities || [];
    if (capabilities.indexOf('ccsds-completeness') !== -1) {
      this.legendOptions = [
        {
          id: 'completeness',
          name: 'Completeness',
          bg: makeGradient(COMPLETENESS_BG),
          fg: COMPLETENESS_FG.toCssString(),
          checked: true,
        },
        ... this.legendOptions,
      ];
    }

    this.processor$.next(yamcs.getProcessor());
    if (yamcs.processor) {
      this.processorSubscription = this.yamcs.yamcsClient.createProcessorSubscription({
        instance: yamcs.instance!,
        processor: yamcs.processor,
      }, processor => {
        this.processor$.next(processor);
      });
    }

    this.filterForm = new UntypedFormGroup({});
    const queryParams = this.route.snapshot.queryParamMap;
    for (const option of this.legendOptions) {
      let checked = option.checked;
      if (queryParams.has(option.id)) {
        checked = queryParams.get(option.id) === 'true';
      }
      this.filterForm.addControl(option.id, new UntypedFormControl(checked));
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
    const tooltipPortal = new ComponentPortal(TimelineTooltipComponent);
    this.tooltipInstance = overlayRef.attach(tooltipPortal).instance;
  }

  @HostListener('mouseleave')
  hideTooltip() {
    this.tooltipInstance.hide();
  }

  ngAfterViewInit() {
    // Fetch archive packets to ensure we can always show bands
    // even if there's no data for the visible range
    this.yamcs.yamcsClient.getPacketNames(this.yamcs.instance!).then(response => {
      this.packetNames = response.packets || [];
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

    this.timeline.addViewportDoubleClickListener(event => {
      const processor = this.yamcs.getProcessor();
      if (processor?.replay) {
        this.yamcs.yamcsClient.editReplayProcessor(this.yamcs.instance!, this.yamcs.processor!, {
          seek: new Date(event.time).toISOString(),
        }).catch(err => this.messageService.showError(err));
      }
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

    const locator = new TimeLocator(this.timeline);
    locator.time = this.yamcs.getMissionTime().getTime();

    const replayOverlay = new ReplayOverlay(this.timeline);
    this.subscriptions.push(
      this.synchronizer.sync(() => {
        locator.time = this.yamcs.getMissionTime().getTime();

        const replayRequest = this.processor$.value?.replayRequest;
        replayOverlay.replayRequest = replayRequest;
      })
    );

    new MouseTracker(this.timeline);
    const axis = new TimeRuler(this.timeline);
    axis.contentHeight = 20;
    axis.label = 'UTC';
    axis.timezone = 'UTC';
    axis.frozen = true;
    axis.fullHeight = true;

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

  fitSelection() {
    var range = this.rangeSelection$.value;
    if (range) {
      this.timeline.setViewRange(range.start.getTime(), range.stop.getTime());
      this.timeline.clearSelection();
    }
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
    const dialogRef = this.dialog.open(JumpToDialogComponent, {
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
    this.timeline.tool = tool;
  }

  replayRange() {
    const currentRange = this.rangeSelection$.value;
    if (currentRange) {
      const processor = this.processor$.value;
      if (processor?.replay) {
        this.modifyReplay(processor, currentRange);
      } else {
        this.startReplay(currentRange);
      }
    }
  }

  private startReplay(range: DateRange) {
    this.dialog.open(StartReplayDialogComponent, {
      width: '400px',
      data: {
        start: range.start,
        stop: range.stop,
      },
    }).afterClosed().subscribe(result => {
      if (result) {
        this.snackBar.open('Initializing replay...', undefined, {
          horizontalPosition: 'end',
        });
        this.yamcs.yamcsClient.createProcessor(result).then(() => {
          this.yamcs.switchContext(this.yamcs.instance!, result.name);
          this.snackBar.open('Joining replay', undefined, {
            duration: 3000,
            horizontalPosition: 'end',
          });
        }).catch(err => {
          this.snackBar.open('Failed to initialize replay', undefined, {
            duration: 3000,
            horizontalPosition: 'end',
          });
        });
      }
    });
  }

  private modifyReplay(processor: Processor, range: DateRange) {
    this.dialog.open(ModifyReplayDialogComponent, {
      width: '400px',
      data: {
        start: range.start,
        stop: range.stop,
      },
    }).afterClosed().subscribe((result: EditReplayProcessorRequest) => {
      if (result) {
        this.snackBar.open('Updating replay...', undefined, {
          horizontalPosition: 'end',
        });
        this.yamcs.yamcsClient.editReplayProcessor(processor.instance, processor.name, result).catch(err => {
          this.messageService.showError(err);
        }).finally(() => this.snackBar.dismiss());
      }
    });
  }

  enableLoop(processor: Processor, loop: boolean) {
    this.yamcs.yamcsClient.editReplayProcessor(processor.instance, processor.name, {
      loop,
    }).catch(err => this.messageService.showError(err));
  }

  downloadDump() {
    const currentRange = this.rangeSelection$.value;
    if (currentRange) {
      this.dialog.open(DownloadDumpDialogComponent, {
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
    const start = utils.clampDate(this.timeline.start - viewportRange).toISOString();
    const stop = utils.clampDate(this.timeline.stop + viewportRange).toISOString();

    const mergeTime = Math.min(Math.floor(viewportRange / 1000), 0x7FFFFFFF);

    let completenessPromise: Promise<ArchiveRecordGroup[]> = Promise.resolve([]);
    if (this.filterForm.value['completeness']) {
      completenessPromise = this.yamcs.yamcsClient.streamCompletenessIndex(
        this.yamcs.instance!, { start, stop, mergeTime }).then(records => {
          return this.groupRecordsByName(records);
        });
    }

    let tmPromise: Promise<ArchiveRecordGroup[]> = Promise.resolve([]);
    if (this.filterForm.value['packets']) {
      tmPromise = this.yamcs.yamcsClient.streamPacketIndex(
        this.yamcs.instance!, { start, stop, mergeTime }).then(records => this.groupRecordsByName(records));
    }

    let parameterPromise: Promise<ArchiveRecordGroup[]> = Promise.resolve([]);
    if (this.filterForm.value['parameters']) {
      parameterPromise = this.yamcs.yamcsClient.streamParameterIndex(
        this.yamcs.instance!, { start, stop, mergeTime }).then(records => this.groupRecordsByName(records));
    }

    let commandPromise: Promise<ArchiveRecordGroup[]> = Promise.resolve([]);
    if (this.filterForm.value['commands']) {
      commandPromise = this.yamcs.yamcsClient.streamCommandIndex(
        this.yamcs.instance!, { start, stop, mergeTime }).then(records => this.groupRecordsByName(records));
    }

    let eventPromise: Promise<ArchiveRecordGroup[]> = Promise.resolve([]);
    if (this.filterForm.value['events']) {
      eventPromise = this.yamcs.yamcsClient.streamEventIndex(
        this.yamcs.instance!, { start, stop, mergeTime }).then(records => this.groupRecordsByName(records));
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

      for (const band of this.timeline.getBands()) {
        if (!(band instanceof TimeRuler)) {
          this.timeline.removeChild(band);
        }
      }
      completenessGroups.sort((a, b) => a.name.localeCompare(b.name));
      for (let i = 0; i < completenessGroups.length; i++) {
        if (i === 0) {
          new TitleBand(this.timeline, 'Completeness');
        }
        const group = completenessGroups[i];
        const band = new IndexGroupBand(this.timeline, group.name, COMPLETENESS_BG, COMPLETENESS_FG, this.formatter);
        band.borderWidth = i === completenessGroups.length - 1 ? 1 : 0;
        band.marginBottom = i === completenessGroups.length - 1 ? 20 : 0;
        band.setupTooltip(this.tooltipInstance);
        band.loadData(group);
      }

      if (this.filterForm.value['packets']) {
        for (let i = 0; i < this.packetNames.length; i++) {
          if (i === 0) {
            new TitleBand(this.timeline, 'Packets');
          }
          const packetName = this.packetNames[i];
          const band = new IndexGroupBand(this.timeline, packetName, PACKETS_BG, PACKETS_FG, this.formatter);
          band.borderWidth = i === this.packetNames.length - 1 ? 1 : 0;
          band.marginBottom = i === this.packetNames.length - 1 ? 20 : 0;
          band.setupTooltip(this.tooltipInstance);
          const group = tmGroups.find(candidate => candidate.name === packetName);
          if (group) {
            band.loadData(group);
          }
        }
      }

      parameterGroups.sort((a, b) => a.name.localeCompare(b.name));
      for (let i = 0; i < parameterGroups.length; i++) {
        if (i === 0) {
          new TitleBand(this.timeline, 'Parameter Groups');
        }
        const group = parameterGroups[i];
        const band = new IndexGroupBand(this.timeline, group.name, PARAMETERS_BG, PARAMETERS_FG, this.formatter);
        band.borderWidth = i === parameterGroups.length - 1 ? 1 : 0;
        band.marginBottom = i === parameterGroups.length - 1 ? 20 : 0;
        band.setupTooltip(this.tooltipInstance);
        band.loadData(group);
      }

      commandGroups.sort((a, b) => a.name.localeCompare(b.name));
      for (let i = 0; i < commandGroups.length; i++) {
        if (i === 0) {
          new TitleBand(this.timeline, 'Commands');
        }
        const group = commandGroups[i];
        const band = new IndexGroupBand(this.timeline, group.name, COMMANDS_BG, COMMANDS_FG, this.formatter);
        band.borderWidth = i === commandGroups.length - 1 ? 1 : 0;
        band.marginBottom = i === commandGroups.length - 1 ? 30 : 0;
        band.setupTooltip(this.tooltipInstance);
        band.loadData(group);
      }

      eventGroups.sort((a, b) => a.name.localeCompare(b.name));
      for (let i = 0; i < eventGroups.length; i++) {
        if (i === 0) {
          new TitleBand(this.timeline, 'Events');
        }
        const group = eventGroups[i];
        const band = new IndexGroupBand(this.timeline, group.name, EVENTS_BG, EVENTS_FG, this.formatter);
        band.borderWidth = i === eventGroups.length - 1 ? 1 : 0;
        band.marginBottom = i === eventGroups.length - 1 ? 20 : 0;
        band.setupTooltip(this.tooltipInstance);
        band.loadData(group);
      }
    });
  }

  private groupRecordsByName(records: ArchiveRecord[]) {
    const groupsByName = new Map<String, ArchiveRecordGroup>();
    for (const record of records) {
      let group = groupsByName.get(record.id.name);
      if (!group) {
        group = { name: record.id.name, records: [] };
        groupsByName.set(record.id.name, group);
      }
      group.records.push(record);
    }
    return [...groupsByName.values()];
  }

  ngOnDestroy() {
    this.processorSubscription?.cancel();
    this.subscriptions.forEach(subscription => subscription.unsubscribe());
    this.timeline.disconnect();
  }
}
