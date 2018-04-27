import { Component, ChangeDetectionStrategy, AfterViewInit, ViewChild, ElementRef } from '@angular/core';

import { Instance } from '@yamcs/client';
import { Timeline } from '@yamcs/timeline';
import { Title } from '@angular/platform-browser';
import { YamcsService } from '../../core/services/YamcsService';
import { ActivatedRoute, Router } from '@angular/router';
import { Overlay } from '@angular/cdk/overlay';
import { ComponentPortal } from '@angular/cdk/portal';
import { TimelineTooltip } from './TimelineTooltip';

@Component({
  templateUrl: './ArchivePage.html',
  styleUrls: ['./ArchivePage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ArchivePage implements AfterViewInit {

  @ViewChild('container')
  container: ElementRef;

  instance: Instance;
  missionTime: Date;

  timeline: Timeline;

  constructor(
    title: Title,
    private yamcs: YamcsService,
    private route: ActivatedRoute,
    private router: Router,
    private overlay: Overlay,
  ) {
    title.setTitle('Archive - Yamcs');
    this.missionTime = yamcs.getMissionTime();
    this.instance = yamcs.getInstance();

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
    this.timeline = new Timeline(this.container.nativeElement, {
      initialDate: this.missionTime.toISOString(),
      zoom: 10,
      pannable: 'X_ONLY',
    }).on('viewportChanged', () => {
      this.router.navigate([], {
        relativeTo: this.route,
        queryParamsHandling: 'merge',
        queryParams: {
          c: this.timeline.visibleCenter.toISOString(),
          z: this.timeline.getZoom(),
        }
      });
    }).on('eventMouseEnter', evt => {
      console.log('have on mouse enter', evt);
    }).on('loadRange', () => {
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
}
