import { Component, ChangeDetectionStrategy, AfterViewInit, ViewChild, ElementRef, OnDestroy } from '@angular/core';

import { Instance } from '@yamcs/client';
import { Timeline } from '@yamcs/timeline';
import { Title } from '@angular/platform-browser';
import { YamcsService } from '../../core/services/YamcsService';
import { ActivatedRoute, Router } from '@angular/router';
import { Overlay } from '@angular/cdk/overlay';
import { ComponentPortal } from '@angular/cdk/portal';
import { TimelineTooltip } from './TimelineTooltip';
import { PreferenceStore } from '../../core/services/PreferenceStore';
import { Subscription } from 'rxjs/Subscription';
import { TimelineOptions } from '../../../../../timeline/dist/types/options';

@Component({
  templateUrl: './ArchivePage.html',
  styleUrls: ['./ArchivePage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ArchivePage implements AfterViewInit, OnDestroy {

  @ViewChild('container')
  container: ElementRef;

  instance: Instance;
  missionTime: Date;

  timeline: Timeline;

  private darkModeSubscription: Subscription;

  constructor(
    title: Title,
    private yamcs: YamcsService,
    private preferenceStore: PreferenceStore,
    private route: ActivatedRoute,
    private router: Router,
    private overlay: Overlay,
  ) {
    title.setTitle('Archive - Yamcs');
    this.missionTime = yamcs.getMissionTime();
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
      originX: 'end',
      originY: 'top',
    }, {
      overlayX: 'start',
      overlayY: 'top',
    });

    const overlayRef = this.overlay.create({ positionStrategy });
    const userProfilePortal = new ComponentPortal(TimelineTooltip);
    overlayRef.attach(userProfilePortal);
  }

  ngAfterViewInit() {
    // Initialize only after a timeout, because otherwise
    // we get the wrong width from the container. Not sure why
    // but it reports 200px too much without the timeout. Possibly
    // comes from the sidebar which has not fully initialized yet.
    window.setTimeout(() => this.initializeTimeline());
  }

  initializeTimeline() {
    const opts: TimelineOptions = {
      initialDate: this.missionTime.toISOString(),
      zoom: 10,
      pannable: 'X_ONLY',
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
        }
      });
    });

    this.timeline.on('eventMouseEnter', evt => {
      console.log('have on mouse enter', evt);
    });

    this.timeline.on('loadRange', () => {
      this.yamcs.getInstanceClient()!.getPacketIndex({
        limit: 1000,
      }).then(groups => {
        const bands = [];
        for (const group of groups) {
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

  refresh() {
    console.log('what ', (this.timeline as any).width, this.timeline.visibleWidth);
  }

  ngOnDestroy() {
    if (this.darkModeSubscription) {
      this.darkModeSubscription.unsubscribe();
    }
  }
}
