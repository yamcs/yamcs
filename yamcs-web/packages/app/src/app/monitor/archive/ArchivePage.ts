import { Overlay } from '@angular/cdk/overlay';
import { ComponentPortal } from '@angular/cdk/portal';
import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, HostListener, OnDestroy, ViewChild } from '@angular/core';
import { MatDialog, MatSnackBar } from '@angular/material';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { Instance } from '@yamcs/client';
import { Event, Range, Timeline, TimelineOptions } from '@yamcs/timeline';
import { BehaviorSubject, Subscription } from 'rxjs';
import { PreferenceStore } from '../../core/services/PreferenceStore';
import { YamcsService } from '../../core/services/YamcsService';
import { DateTimePipe } from '../../shared/pipes/DateTimePipe';
import { StartReplayDialog } from '../template/StartReplayDialog';
import { DownloadDumpDialog } from './DownloadDumpDialog';
import { JumpToDialog } from './JumpToDialog';
import { TimelineTooltip } from './TimelineTooltip';


@Component({
  templateUrl: './ArchivePage.html',
  styleUrls: ['./ArchivePage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ArchivePage implements AfterViewInit, OnDestroy {

  @ViewChild('container')
  container: ElementRef;

  instance: Instance;

  timeline: Timeline;

  rangeSelection$ = new BehaviorSubject<Range | null>(null);
  viewportRange$ = new BehaviorSubject<Range | null>(null);

  private tooltipInstance: TimelineTooltip;
  private darkModeSubscription: Subscription;

  private timeInfoSubscription: Subscription;

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
    title.setTitle('TM Archive - Yamcs');
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

    yamcs.getInstanceClient()!.getTimeUpdates().then(response => {
      this.timeInfoSubscription = response.timeInfo$.subscribe(timeInfo => {
        if (this.timeline) {
          this.timeline.setWallclockTime(timeInfo.currentTimeUTC);
        }
      });
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
    this.yamcs.getInstanceClient()!.getPacketNames().then(packetNames => {
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
      this.router.navigate([], {
        relativeTo: this.route,
        queryParamsHandling: 'merge',
        queryParams: {
          c: this.timeline.visibleCenter.toISOString(),
          z: this.timeline.getZoom(),
        },
        replaceUrl: true,
      });
    });

    this.timeline.on('eventMouseEnter', evt => {
      const userObject = evt.userObject as Event;
      let ttText = `Start: ${this.dateTimePipe.transform(userObject.start)}<br>`;
      ttText += `Stop:&nbsp; ${this.dateTimePipe.transform(userObject.stop!)}<br>`;
      const sec = (Date.parse(userObject.stop as string) - Date.parse(userObject.start as string)) / 1000;
      ttText += `Count: ${userObject.data.count}`;
      if (userObject.data.count > 1) {
        ttText += ` (${(userObject.data.count / sec).toFixed(3)} Hz)`;
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
      this.yamcs.getInstanceClient()!.getPacketIndex({
        start: evt.loadStart.toISOString(),
        stop: evt.loadStop.toISOString(),
        limit: 1000,
      }).then(groups => {
        const bands = [];
        for (const packetName of this.packetNames) {
          const events: Event[] = [];
          for (const group of groups) {
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
          bands.push({
            id: packetName,
            type: 'EventBand',
            label: packetName,
            // draggable: true,
            interactive: true,
            interactiveSidebar: false,
            wrap: false,
            style: {
              marginTop: 8,
              marginBottom: 8,
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
          this.yamcs.getInstanceClient()!.createProcessor(result).then(() => {
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

  ngOnDestroy() {
    if (this.darkModeSubscription) {
      this.darkModeSubscription.unsubscribe();
    }
    if (this.timeInfoSubscription) {
      this.timeInfoSubscription.unsubscribe();
    }
  }
}
