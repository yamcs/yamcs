import { Component, ChangeDetectionStrategy, AfterViewInit, ViewChild, ElementRef, OnDestroy } from '@angular/core';

import { Instance } from '@yamcs/client';
import { Timeline, Range } from '@yamcs/timeline';
import { Title } from '@angular/platform-browser';
import { YamcsService } from '../../core/services/YamcsService';
import { ActivatedRoute, Router } from '@angular/router';
import { Overlay } from '@angular/cdk/overlay';
import { ComponentPortal } from '@angular/cdk/portal';
import { TimelineTooltip } from './TimelineTooltip';
import { PreferenceStore } from '../../core/services/PreferenceStore';
import { Subscription } from 'rxjs/Subscription';
import { TimelineOptions } from '../../../../../timeline/dist/types/options';
import { DateTimePipe } from '../../shared/pipes/DateTimePipe';
import { BehaviorSubject } from 'rxjs/BehaviorSubject';
import { MatDialog } from '@angular/material';
import { CreateDownloadDialog } from './CreateDownloadDialog';

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

  private tooltipInstance: TimelineTooltip;
  private darkModeSubscription: Subscription;

  constructor(
    title: Title,
    private yamcs: YamcsService,
    private preferenceStore: PreferenceStore,
    private route: ActivatedRoute,
    private router: Router,
    private overlay: Overlay,
    private dialog: MatDialog,
    private dateTimePipe: DateTimePipe,
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

  ngAfterViewInit() {
    // Initialize only after a timeout, because otherwise
    // we get the wrong width from the container. Not sure why
    // but it reports 200px too much without the timeout. Possibly
    // comes from the sidebar which has not fully initialized yet.
    window.setTimeout(() => this.initializeTimeline());
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
      style: {
        sidebarWidth: 200,
      }
    };
    if (this.preferenceStore.isDarkMode()) {
      opts.theme = 'dark';
    }
    this.timeline = new Timeline(this.container.nativeElement, opts);

    this.timeline.on('viewportChanged', () => {
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
      const userObject = evt.userObject as any;
      let ttText = `Start: ${this.dateTimePipe.transform(userObject.start)}<br>`;
      ttText += `Stop:&nbsp; ${this.dateTimePipe.transform(userObject.stop)}<br>`;
      const sec = (Date.parse(userObject.stop) - Date.parse(userObject.start)) / 1000;
      ttText += `Count: ${userObject.count} (${(userObject.count / sec).toFixed(3)} Hz)`;
      this.tooltipInstance.show(ttText, evt.clientX, evt.clientY);
    });

    this.timeline.on('eventClick', evt => {
      const userObject = evt.userObject as any;
      this.timeline.selectRange(userObject.start, userObject.stop);
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

    this.timeline.on('loadRange', () => {
      this.yamcs.getInstanceClient()!.getPacketIndex({
        limit: 1000,
      }).then(groups => {
        const bands = [];
        for (const group of groups) {
          const events: any[] = group.entry;
          for (const event of events) {
            const sec = (Date.parse(event.stop) - Date.parse(event.start)) / 1000;
            event.title = `${(event.count / sec).toFixed(1)} Hz`;
          }
          bands.push({
            type: 'EventBand',
            label: group.id.name,
            interactive: true,
            interactiveSidebar: false,
            style: {
              wrap: false,
              marginTop: 8,
              marginBottom: 8,
            },
            events: group.entry,
          });
        }
        this.timeline.setData({
          header: [
            { type: 'Timescale', label: 'UTC', tz: 'UTC' },
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

  zoomIn() {
    this.timeline.zoomIn();
  }

  zoomOut() {
    this.timeline.zoomOut();
  }

  createDownload() {
    const currentRange = this.rangeSelection$.value;
    if (currentRange) {
      const dialogRef = this.dialog.open(CreateDownloadDialog, {
        width: '400px',
        data: {
          start: currentRange.start,
          stop: currentRange.stop,
        },
      });

      dialogRef.afterClosed().subscribe(result => {
        if (result) {
          this.yamcs.getInstanceClient()!.getEventsDownloadURL({
            start: result.start.toISOString(),
            stop: result.stop.toISOString(),
            format: 'csv',
          });
        }
      });
    }
  }

  ngOnDestroy() {
    if (this.darkModeSubscription) {
      this.darkModeSubscription.unsubscribe();
    }
  }
}
