import {
  AfterViewInit,
  Component,
  ChangeDetectionStrategy,
  ElementRef,
  ViewChild,
} from '@angular/core';
import { Observable } from 'rxjs/Observable';
import { take } from 'rxjs/operators';

import { Alias, DisplayFolder } from '../../../yamcs-client';
import { YamcsService } from '../../core/services/YamcsService';
import { ResourceResolver } from './ResourceResolver';
import { Layout, LayoutListener, LayoutStateListener } from './Layout';
import { DisplayFrame, Coordinates } from './DisplayFrame';
import { LayoutState } from './LayoutState';
import { BehaviorSubject } from 'rxjs/BehaviorSubject';

@Component({
  templateUrl: './DisplaysPage.html',
  styleUrls: ['./DisplaysPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DisplaysPage implements AfterViewInit, LayoutListener, LayoutStateListener {

  @ViewChild('displayContainer')
  displayContainerRef: ElementRef;

  displayInfo$: Observable<DisplayFolder>;

  resourceResolver: ResourceResolver;
  layout: Layout;

  navigatorOpen$ = new BehaviorSubject<boolean>(true);

  constructor(private yamcs: YamcsService) {
    this.displayInfo$ = yamcs.getSelectedInstance().getDisplayInfo();
    this.resourceResolver = new UssResourceResolver(yamcs);
  }

  ngAfterViewInit() {
    const targetEl = this.displayContainerRef.nativeElement;
    this.layout = new Layout(targetEl, this.resourceResolver);
    this.layout.layoutListeners.add(this);

    // Attempt to restore state from session storage.
    // This way refresh or navigation don't just throw away all opened displays
    const instance = this.yamcs.getSelectedInstance().instance;
    const item = sessionStorage.getItem(`yamcs.${instance}.layout`);
    if (item) {
      const state = JSON.parse(item) as LayoutState;
      this.restoreState(state);
    }

    this.layout.layoutStateListeners.add(this);
  }

  openDisplay(id: string, coordinates?: Coordinates) {
    if (!this.layout) {
      return;
    }
    const existingFrame = this.layout.getDisplayFrame(id);
    if (existingFrame) {
      this.layout.bringToFront(existingFrame);
    } else {
      this.layout.createDisplayFrame(id, coordinates);
    }
  }

  toggleNavigator() {
    this.navigatorOpen$.next(!this.navigatorOpen$.getValue());
  }

  tileDisplays() {
    this.layout.tileFrames();
  }

  cascadeDisplays() {
    this.layout.cascadeFrames();
  }

  onDisplayFrameOpen(frame: DisplayFrame) {
    const opsNames = frame.getOpsNames();
    if (opsNames.size) {
      const ids: Alias[] = [];
      opsNames.forEach(opsName => ids.push({
        namespace: 'MDB:OPS Name',
        name: opsName,
      }));

      this.yamcs.getSelectedInstance().getParameterValueUpdates({
        id: ids,
        abortOnInvalid: false,
        sendFromCache: true,
        updateOnExpiration: true,
      }).subscribe(evt => {
        frame.processParameterValues(evt.parameter);
      });
    }
  }

  onDisplayFrameClose(frame: DisplayFrame) {
    // TODO unsubscribe
  }

  onStateChange(state: LayoutState) {
    const instance = this.yamcs.getSelectedInstance().instance;
    sessionStorage.setItem(`yamcs.${instance}.layout`, JSON.stringify(state));
  }

  restoreState(state: LayoutState) {
    for (const frameState of state.frames) {
      this.openDisplay(frameState.id, {
        x: frameState.x,
        y: frameState.y,
        width: frameState.width,
        height: frameState.height,
      });
    }
  }
}

/**
 * Resolves USS resources by fetching them from the server as
 * a static file.
 */
class UssResourceResolver implements ResourceResolver {

  constructor(private yamcsService: YamcsService) {
  }

  resolvePath(path: string) {
    return `${this.yamcsService.yamcsClient.staticUrl}/${path}`;
  }

  retrieveText(path: string) {
    return this.yamcsService.yamcsClient.getStaticText(path).pipe(take(1)).toPromise();
  }

  retrieveXML(path: string) {
    return this.yamcsService.yamcsClient.getStaticXML(path).pipe(take(1)).toPromise();
  }

  retrieveXMLDisplayResource(path: string) {
    const instance = this.yamcsService.getSelectedInstance().instance;
    const displayPath = `${instance}/displays/${path}`;
    return this.yamcsService.yamcsClient.getStaticXML(displayPath).pipe(take(1)).toPromise();
  }
}
