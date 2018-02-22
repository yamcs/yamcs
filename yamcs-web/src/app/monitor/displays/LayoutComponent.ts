
import {
  Component,
  ViewChild,
  ElementRef,
  ChangeDetectionStrategy,
  AfterViewInit,
  Output,
  EventEmitter,
  Input,
  OnInit,
} from '@angular/core';
import { ResourceResolver } from './ResourceResolver';
import { Layout, LayoutStateListener, LayoutListener } from './Layout';
import { LayoutState } from './LayoutState';
import { YamcsService } from '../../core/services/YamcsService';
import { take } from 'rxjs/operators';
import { DisplayFrame, Coordinates } from './DisplayFrame';
import { DisplayFolder } from '../../../yamcs-client';
import { Observable } from 'rxjs/Observable';
import { BehaviorSubject } from 'rxjs/BehaviorSubject';

@Component({
  selector: 'app-layout-component',
  templateUrl: './LayoutComponent.html',
  styleUrls: ['./LayoutComponent.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LayoutComponent implements OnInit, AfterViewInit, LayoutListener, LayoutStateListener {

  @Input()
  startWithOpenedNavigator = true;

  @Input()
  layoutState: LayoutState = { frames: [] };

  @Output()
  stateChange = new EventEmitter<LayoutState>();

  @ViewChild('displayContainer')
  private displayContainerRef: ElementRef;

  displayInfo$: Observable<DisplayFolder>;
  showNavigator$: BehaviorSubject<boolean>;

  private resourceResolver: ResourceResolver;
  private layout: Layout;

  constructor(private yamcs: YamcsService) {
    this.displayInfo$ = yamcs.getSelectedInstance().getDisplayInfo();
    this.resourceResolver = new RemoteResourceResolver(yamcs);
  }

  ngOnInit() {
    this.showNavigator$ = new BehaviorSubject<boolean>(this.startWithOpenedNavigator);
  }

  ngAfterViewInit() {
    const targetEl = this.displayContainerRef.nativeElement;
    this.layout = new Layout(targetEl, this.resourceResolver);
    this.layout.layoutListeners.add(this);
    this.restoreState(this.layoutState).then(() => {
      this.layout.layoutStateListeners.add(this);
    });
  }

  openDisplay(id: string, coordinates?: Coordinates): Promise<void> {
    if (this.layout) {
      const existingFrame = this.layout.getDisplayFrame(id);
      if (existingFrame) {
        this.layout.bringToFront(existingFrame);
      } else {
        return this.layout.createDisplayFrame(id, coordinates);
      }
    }
    return Promise.resolve();
  }

  tileFrames() {
    this.layout.tileFrames();
  }

  cascadeFrames() {
    this.layout.cascadeFrames();
  }

  clear() {
    this.layout.clear();
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
    return this.layout.getLayoutState();
  }

  private restoreState(state: LayoutState) {
    const openPromises = [];
    for (const frameState of state.frames) {
      openPromises.push(this.openDisplay(frameState.id, {
        x: frameState.x,
        y: frameState.y,
        width: frameState.width,
        height: frameState.height,
      }));
    }
    return Promise.all(openPromises);
  }

  toggleNavigator() {
    this.showNavigator$.next(!this.showNavigator$.getValue());
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
