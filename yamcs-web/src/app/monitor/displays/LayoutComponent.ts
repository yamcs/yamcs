
import { Component, ViewChild, ElementRef, ChangeDetectionStrategy, AfterViewInit, Output, EventEmitter, Input } from '@angular/core';
import { ResourceResolver } from './ResourceResolver';
import { Layout, LayoutStateListener, LayoutListener } from './Layout';
import { LayoutState } from './LayoutState';
import { YamcsService } from '../../core/services/YamcsService';
import { take } from 'rxjs/operators';
import { DisplayFrame, Coordinates } from './DisplayFrame';
import { DisplayFolder } from '../../../yamcs-client';
import { Observable } from 'rxjs/Observable';

@Component({
  selector: 'app-layout-component',
  templateUrl: './LayoutComponent.html',
  styleUrls: ['./LayoutComponent.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LayoutComponent implements AfterViewInit, LayoutListener, LayoutStateListener {

  @Input()
  showNavigator = true;

  @Output()
  stateChange = new EventEmitter<LayoutState>();

  @ViewChild('displayContainer')
  private displayContainerRef: ElementRef;


  displayInfo$: Observable<DisplayFolder>;

  private resourceResolver: ResourceResolver;
  private layout: Layout;

  constructor(private yamcs: YamcsService) {
    this.displayInfo$ = yamcs.getSelectedInstance().getDisplayInfo();
    this.resourceResolver = new RemoteResourceResolver(yamcs);
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

  tileFrames() {
    this.layout.tileFrames();
  }

  cascadeFrames() {
    this.layout.cascadeFrames();
  }

  onDisplayFrameOpen(frame: DisplayFrame) {
    const ids = frame.getParameterIds();
    if (ids.length) {
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
    this.stateChange.emit(state);
  }

  getLayoutState() {
    this.layout.getLayoutState();
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
 * Resolves resources by fetching them from the server as
 * a static file.
 */
class RemoteResourceResolver implements ResourceResolver {

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
