import { Overlay } from '@angular/cdk/overlay';
import { ComponentPortal } from '@angular/cdk/portal';
import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, HostListener, OnDestroy, ViewChild } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Params, Router } from '@angular/router';
import { BehaviorSubject, Subscription } from 'rxjs';
import { IndexGroup } from '../client';
import { PreferenceStore } from '../core/services/PreferenceStore';
import { YamcsService } from '../core/services/YamcsService';
import { DateTimePipe } from '../shared/pipes/DateTimePipe';
import { StartReplayDialog } from '../shared/template/StartReplayDialog';
import { DownloadDumpDialog } from './DownloadDumpDialog';
import { JumpToDialog } from './JumpToDialog';
import { Event, Range, Timeline, TimelineOptions } from './timeline';
import { TimelineTooltip } from './TimelineTooltip';


@Component({
  templateUrl: './ArchiveOverviewPage.html',
  styleUrls: ['./ArchiveOverviewPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ArchiveOverviewPage implements AfterViewInit, OnDestroy {

  completenessBg = 'orange';
  completenessFg = 'rgb(173, 94, 0)';
  packetsBg = 'palegoldenrod';
  packetsFg = '#555';
  parametersBg = 'navajowhite';
  parametersFg = '#1c4b8b';
  commandsBg = '#ffcc00';
  commandsFg = '#1c4b8b';
  // eventsBg = '#ffff66';
  // eventsFg = '#1c4b8b';

  legendOptions = [
    { id: 'completeness', name: 'Completeness', bg: this.completenessBg, fg: this.completenessFg, checked: true },
    { id: 'packets', name: 'Packets', bg: this.packetsBg, fg: this.packetsFg, checked: true },
    { id: 'parameters', name: 'Parameters', bg: this.parametersBg, fg: this.parametersFg, checked: true },
    { id: 'commands', name: 'Commands', bg: this.commandsBg, fg: this.commandsFg, checked: false },
    // { id: 'events', name: 'Events', bg: this.eventsBg, fg: this.eventsFg, checked: false },
  ];

  @ViewChild('container', { static: true })
  container: ElementRef;

  filterForm: FormGroup;

  instance: string;

  timeline: Timeline;

  rangeSelection$ = new BehaviorSubject<Range | null>(null);
  viewportRange$ = new BehaviorSubject<Range | null>(null);

  private tooltipInstance: TimelineTooltip;
  private darkModeSubscription: Subscription;

  private timeSubscription: Subscription;

  private packetNames: string[] = [];

  constructor(
    title: Title,
    private yamcs: YamcsService,
    private preferenceStore: PreferenceStore,
    private route: ActivatedRoute,
    private router: Router,
    private overlay: Overlay,
    private dialog: MatDialog,
    private dateTimePipe: DateTimePipe,
    private snackBar: MatSnackBar,
  ) {
    title.setTitle('Archive Overview');
    this.instance = yamcs.getInstance();

    this.darkModeSubscription = preferenceStore.darkMode$.subscribe(darkMode => {
      if (this.timeline) {
        if (darkMode) {
          this.timeline.updateOptions({ theme: 'dark' });
        } else {
          this.timeline.updateOptions({ theme: 'base' });
        }
      }
    });

    this.filterForm = new FormGroup({});
    const queryParams = this.route.snapshot.queryParamMap;
    for (const option of this.legendOptions) {
      let checked = option.checked;
      if (queryParams.has(option.id)) {
        checked = queryParams.get(option.id) === 'true';
      }
      this.filterForm.addControl(option.id, new FormControl(checked));
    }

    this.timeSubscription = yamcs.time$.subscribe(time => {
      if (this.timeline && time) {
        this.timeline.setWallclockTime(time);
      }
    });

    const bodyRef = new ElementRef(document.body);
    const positionStrategy = this.overlay.position().connectedTo(bodyRef, {
      originX: 'start',
      originY: 'top',
    }, {
      overlayX: 'start',
      overlayY: 'top',
    });

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
    this.yamcs.yamcsClient.getPacketNames(this.instance).then(packetNames => {
      this.packetNames = packetNames;

      // Initialize only after a timeout, because otherwise
      // we get the wrong width from the container. Not sure why
      // but it reports 200px too much without the timeout. Possibly
      // comes from the sidebar which has not fully initialized yet.
      window.setTimeout(() => this.initializeTimeline());
    });
  }

  initializeTimeline() {
    const queryParams = this.route.snapshot.queryParamMap;
    const c = queryParams.get('c');
    let z;
    if (queryParams.has('z')) {
      z = Number(queryParams.get('z'));
    }
    const opts: TimelineOptions = {
      initialDate: c || this.yamcs.getMissionTime().toISOString(),
      zoom: z || 12,
      pannable: 'X_ONLY',
      wallclock: false,
      sidebarWidth: 200,
    };
    if (this.preferenceStore.isDarkMode()) {
      opts.theme = 'dark';
    }
    this.timeline = new Timeline(this.container.nativeElement, opts);

    this.timeline.on('viewportChanged', () => {
      this.viewportRange$.next({
        start: this.timeline.visibleStart,
        stop: this.timeline.visibleStop,
      });
      const legendParams: Params = {};
      for (const option of this.legendOptions) {
        legendParams[option.id] = this.filterForm.value[option.id];
      }
      this.router.navigate([], {
        relativeTo: this.route,
        queryParamsHandling: 'merge',
        queryParams: {
          c: this.timeline.visibleCenter.toISOString(),
          z: this.timeline.getZoom(),
          ...legendParams,
        },
        replaceUrl: true,
      });
    });

    this.timeline.on('eventMouseEnter', evt => {
      const userObject = evt.userObject as Event;
      let ttText = `Start: ${this.dateTimePipe.transform(userObject.start)}<br>`;
      ttText += `Stop:&nbsp; ${this.dateTimePipe.transform(userObject.stop!)}<br>`;
      if (userObject.data.count >= 0) {
        const sec = (Date.parse(userObject.stop as string) - Date.parse(userObject.start as string)) / 1000;
        ttText += `Count: ${userObject.data.count}`;
        if (userObject.data.count > 1) {
          ttText += ` (${(userObject.data.count / sec).toFixed(3)} Hz)`;
        }
      } else if (userObject.data.description) {
        ttText += userObject.data.description;
      }
      this.tooltipInstance.show(ttText, evt.clientX, evt.clientY);
    });

    this.timeline.on('eventClick', evt => {
      const userObject = evt.userObject as Event;
      this.timeline.selectRange(userObject.start, userObject.stop!);
    });

    this.timeline.on('grabStart', () => {
      this.tooltipInstance.hide();
    });

    this.timeline.on('eventMouseLeave', evt => {
      this.tooltipInstance.hide();
    });

    this.timeline.on('viewportHover', evt => {
      if (evt.x === undefined) {
        this.tooltipInstance.hide();
      }
    });

    this.timeline.on('rangeSelectionChanged', evt => {
      this.rangeSelection$.next(evt.range || null);
    });

    this.timeline.on('loadRange', evt => {

      let completenessPromise: Promise<IndexGroup[]> = Promise.resolve([]);
      if (this.filterForm.value['completeness']) {
        completenessPromise = this.yamcs.yamcsClient.getCompletenessIndex(this.instance, {
          start: evt.loadStart.toISOString(),
          stop: evt.loadStop.toISOString(),
          limit: 1000,
        });
      }

      let tmPromise: Promise<IndexGroup[]> = Promise.resolve([]);
      if (this.filterForm.value['packets']) {
        tmPromise = this.yamcs.yamcsClient.getPacketIndex(this.instance, {
          start: evt.loadStart.toISOString(),
          stop: evt.loadStop.toISOString(),
          limit: 1000,
        });
      }

      let parameterPromise: Promise<IndexGroup[]> = Promise.resolve([]);
      if (this.filterForm.value['parameters']) {
        parameterPromise = this.yamcs.yamcsClient.getParameterIndex(this.instance, {
          start: evt.loadStart.toISOString(),
          stop: evt.loadStop.toISOString(),
          limit: 1000,
        });
      }

      let commandPromise: Promise<IndexGroup[]> = Promise.resolve([]);
      if (this.filterForm.value['commands']) {
        commandPromise = this.yamcs.yamcsClient.getCommandIndex(this.instance, {
          start: evt.loadStart.toISOString(),
          stop: evt.loadStop.toISOString(),
          limit: 1000,
        });
      }

      Promise.all([
        this.yamcs.yamcsClient.getTags(this.instance, {
          start: evt.loadStart.toISOString(),
          stop: evt.loadStop.toISOString(),
        }),
        completenessPromise,
        tmPromise,
        parameterPromise,
        commandPromise,
      ]).then(responses => {
        const tags = responses[0].tag || [];
        const completenessGroups = responses[1];
        const tmGroups = responses[2];
        const parameterGroups = responses[3];
        const commandGroups = responses[4];

        const bands = [];

        if (tags.length) {
          const events: Event[] = [];
          for (const tag of tags) {
            const event: Event = {
              start: tag.startUTC,
              stop: tag.stopUTC,
              milestone: false,
              title: tag.name,
              backgroundColor: tag.color,
              foregroundColor: 'black',
              data: {
                id: tag.id,
                description: tag.description,
              }
            };
            events.push(event);
          }
          bands.push({
            id: 'Tags',
            type: 'EventBand',
            label: 'Tags',
            interactive: true,
            interactiveSidebar: false,
            style: {
              marginTop: 4,
              marginBottom: 4,
            },
            events,
          });
        }

        for (let i = 0; i < completenessGroups.length; i++) {
          const group = completenessGroups[i];
          const events: Event[] = [];
          for (const entry of group.entry) {
            const event: Event = {
              start: entry.start,
              stop: entry.stop,
              milestone: false,
              data: {
                count: entry.count,
              }
            };
            if (entry.count > 1) {
              const sec = (Date.parse(entry.stop) - Date.parse(entry.start)) / 1000;
              event.title = `${(entry.count / sec).toFixed(1)} Hz`;
            }
            events.push(event);
          }
          const extraStyles: { [key: string]: any; } = {};
          if (i < completenessGroups.length - 1) {
            extraStyles['dividerColor'] = 'transparent';
          }
          bands.push({
            id: group.id.name,
            type: 'EventBand',
            label: group.id.name,
            interactive: true,
            interactiveSidebar: false,
            wrap: false,
            style: {
              ...extraStyles,
              backgroundColor: this.completenessBg,
              textColor: this.completenessFg,
              marginTop: 4,
              marginBottom: 4,
            },
            events,
          });
        }

        if (this.filterForm.value['packets']) {
          for (let i = 0; i < this.packetNames.length; i++) {
            const packetName = this.packetNames[i];
            const events: Event[] = [];
            for (const group of tmGroups) {
              if (group.id.name !== packetName) {
                continue;
              }
              for (const entry of group.entry) {
                const event: Event = {
                  start: entry.start,
                  stop: entry.stop,
                  milestone: false,
                  data: {
                    count: entry.count,
                  }
                };
                if (entry.count > 1) {
                  const sec = (Date.parse(entry.stop) - Date.parse(entry.start)) / 1000;
                  event.title = `${(entry.count / sec).toFixed(1)} Hz`;
                }
                events.push(event);
              }
            }
            const extraStyles: { [key: string]: any; } = {};
            if (i < this.packetNames.length - 1) {
              extraStyles['dividerColor'] = 'transparent';
            }
            bands.push({
              id: packetName,
              type: 'EventBand',
              label: packetName,
              interactive: true,
              interactiveSidebar: false,
              wrap: false,
              style: {
                ...extraStyles,
                backgroundColor: this.packetsBg,
                textColor: this.packetsFg,
                marginTop: 4,
                marginBottom: 4,
              },
              events,
            });
          }
        }

        for (let i = 0; i < parameterGroups.length; i++) {
          const group = parameterGroups[i];
          const events: Event[] = [];
          for (const entry of group.entry) {
            const event: Event = {
              start: entry.start,
              stop: entry.stop,
              milestone: false,
              data: {
                count: entry.count,
              }
            };
            if (entry.count > 1) {
              const sec = (Date.parse(entry.stop) - Date.parse(entry.start)) / 1000;
              event.title = `${(entry.count / sec).toFixed(1)} Hz`;
            }
            events.push(event);
          }
          const extraStyles: { [key: string]: any; } = {};
          if (i < parameterGroups.length - 1) {
            extraStyles['dividerColor'] = 'transparent';
          }
          bands.push({
            id: group.id.name,
            type: 'EventBand',
            label: group.id.name,
            interactive: true,
            interactiveSidebar: false,
            wrap: false,
            style: {
              ...extraStyles,
              backgroundColor: this.parametersBg,
              textColor: this.parametersFg,
              marginTop: 4,
              marginBottom: 4,
            },
            events,
          });
        }

        for (let i = 0; i < commandGroups.length; i++) {
          const group = commandGroups[i];
          const events: Event[] = [];
          for (const entry of group.entry) {
            const event: Event = {
              start: entry.start,
              stop: entry.stop,
              data: {
                count: entry.count,
              }
            };
            if (entry.count > 1) {
              const sec = (Date.parse(entry.stop) - Date.parse(entry.start)) / 1000;
              event.title = `${(entry.count / sec).toFixed(1)} Hz`;
            }
            events.push(event);
          }
          const extraStyles: { [key: string]: any; } = {};
          if (i < commandGroups.length - 1) {
            extraStyles['dividerColor'] = 'transparent';
          }
          bands.push({
            id: group.id.name,
            type: 'EventBand',
            label: group.id.name,
            interactive: true,
            interactiveSidebar: false,
            wrap: false,
            style: {
              ...extraStyles,
              backgroundColor: this.commandsBg,
              textColor: this.commandsFg,
              marginTop: 4,
              marginBottom: 4,
            },
            events,
          });
        }

        this.timeline.setData({
          header: [
            { type: 'Timescale', label: '', tz: 'UTC', grabAction: 'select' },
          ],
          body: bands,
        });
      });
    });

    this.timeline.render();
  }

  jumpToNow() {
    const missionTime = this.yamcs.getMissionTime();
    this.timeline.reveal(missionTime);
  }

  jumpTo() {
    const currentDate = this.timeline.visibleCenter;
    const dialogRef = this.dialog.open(JumpToDialog, {
      width: '400px',
      data: {
        date: currentDate,
      },
    });
    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.timeline.reveal(result.date);
      }
    });
  }

  refresh() {
    const currentDate = this.timeline.visibleCenter;
    this.timeline.reveal(currentDate);
  }

  zoomIn() {
    this.timeline.zoomIn();
  }

  zoomOut() {
    this.timeline.zoomOut();
  }

  goBackward() {
    this.timeline.goBackward();
  }

  goForward() {
    this.timeline.goForward();
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
            this.snackBar.open(`Joined replay ${result.name}`, undefined, {
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
    this.refresh();
  }

  ngOnDestroy() {
    if (this.darkModeSubscription) {
      this.darkModeSubscription.unsubscribe();
    }
    if (this.timeSubscription) {
      this.timeSubscription.unsubscribe();
    }
  }
}
