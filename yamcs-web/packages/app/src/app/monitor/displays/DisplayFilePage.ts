import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, OnDestroy, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Instance } from '@yamcs/client';
import { Display, DisplayHolder, OpenDisplayCommandOptions, OpiDisplay, ParDisplay, UssDisplay } from '@yamcs/displays';
import { BehaviorSubject } from 'rxjs';
import * as screenfull from 'screenfull';
import { YamcsService } from '../../core/services/YamcsService';
import { MyDisplayCommunicator } from './MyDisplayCommunicator';

@Component({
  templateUrl: './DisplayFilePage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DisplayFilePage implements DisplayHolder, AfterViewInit, OnDestroy {

  instance: Instance;

  @ViewChild('displayContainer')
  displayContainer: ElementRef;

  displayId: string;
  filename: string;
  folderLink: string;
  display: Display;

  fullscreen$ = new BehaviorSubject<boolean>(false);
  fullscreenListener: () => void;

  constructor(private yamcs: YamcsService, private router: Router, private route: ActivatedRoute) {
    this.instance = yamcs.getInstance();
    this.fullscreenListener = () => this.fullscreen$.next(screenfull.isFullscreen);
    screenfull.on('change', this.fullscreenListener);

    const url = this.route.snapshot.url;
    let path = '';
    for (let i = 0; i < url.length; i++) {
      if (i === url.length - 1) {
        this.filename = url[i].path;
        this.folderLink = '/monitor/displays/browse' + path;
      }
      path += '/' + url[i].path;
    }
    this.displayId = path;
  }

  ngAfterViewInit() {
    const displayCommunicator = new MyDisplayCommunicator(this.yamcs, this.router);
    if (this.displayId.toLowerCase().endsWith('uss')) {
      this.display = new UssDisplay(this, this.displayContainer.nativeElement, displayCommunicator);
    } else if (this.displayId.toLowerCase().endsWith('opi')) {
      this.display = new OpiDisplay(this, this.displayContainer.nativeElement, displayCommunicator);
    } else if (this.displayId.toLowerCase().endsWith('par')) {
      this.display = new ParDisplay(this, this.displayContainer.nativeElement, displayCommunicator);
    }

    if (this.display) {
      this.display.parseAndDraw(this.displayId).then(() => {
        const ids = this.display.getParameterIds();
        if (ids.length) {
          this.yamcs.getInstanceClient()!.getParameterValueUpdates({
            id: ids,
            abortOnInvalid: false,
            sendFromCache: true,
            updateOnExpiration: true,
          }).then(res => {
            res.parameterValues$.subscribe(pvals => {
              this.display.processParameterValues(pvals);
            });
          });
        }
      });
    }
  }

  goFullscreen() {
    if (screenfull.enabled) {
      screenfull.request(this.displayContainer.nativeElement);
    } else {
      alert('Your browser does not appear to support going full screen');
    }
  }

  getBaseId() { // DisplayHolder
    return this.displayId;
  }

  openDisplay(options: OpenDisplayCommandOptions) { // DisplayHolder
    // TODO (called via e.g. NavigationButton)
  }

  closeDisplay() { // DisplayHolder
    // NOP
  }

  ngOnDestroy() {
    screenfull.off('change', this.fullscreenListener);
  }
}
