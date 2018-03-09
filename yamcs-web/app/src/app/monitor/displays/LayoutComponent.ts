
import {
  Component,
  ViewChild,
  ElementRef,
  ChangeDetectionStrategy,
  Output,
  EventEmitter,
  Input,
  OnInit,
  OnChanges,
} from '@angular/core';
import { ResourceResolver } from './ResourceResolver';
import { Layout, LayoutStateListener, LayoutListener } from './Layout';
import { LayoutState } from './LayoutState';
import { YamcsService } from '../../core/services/YamcsService';
import { DisplayFrame, Coordinates } from './DisplayFrame';
import { DisplayFolder } from '@yamcs/client';
import { BehaviorSubject } from 'rxjs/BehaviorSubject';
import { Store } from '@ngrx/store';
import { State } from '../../app.reducers';
import { selectCurrentInstance } from '../../core/store/instance.selectors';

@Component({
  selector: 'app-layout-component',
  templateUrl: './LayoutComponent.html',
  styleUrls: ['./LayoutComponent.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LayoutComponent implements OnInit, OnChanges, LayoutListener, LayoutStateListener {

  @Input()
  startWithOpenedNavigator = true;

  @Input()
  layoutState: LayoutState = { frames: [] };

  @Output()
  stateChange = new EventEmitter<LayoutState>();

  @ViewChild('displayContainer')
  private displayContainerRef: ElementRef;

  showNavigator$: BehaviorSubject<boolean>;
  displayInfo$ = new BehaviorSubject<DisplayFolder | null>(null);

  private resourceResolver: ResourceResolver;
  private layout: Layout;

  constructor(private yamcs: YamcsService, store: Store<State>) {
    store.select(selectCurrentInstance).subscribe(instance => {
      // TODO should be simpler. yamcs.getSelectedInstance() is not yet updated when this triggers.
      const instanceClient = this.yamcs.yamcsClient.selectInstance(instance.name);
      instanceClient.getDisplayInfo().then(displayInfo => {
        this.displayInfo$.next(displayInfo);
      });
    });
    this.resourceResolver = new RemoteResourceResolver(yamcs);
  }

  ngOnInit() {
    this.showNavigator$ = new BehaviorSubject<boolean>(this.startWithOpenedNavigator);
  }

  ngOnChanges() {
    const targetEl = this.displayContainerRef.nativeElement;
    if (this.layout) {
      this.layout.layoutListeners.delete(this);
      this.layout.layoutStateListeners.delete(this);
      targetEl.innerHTML = '';
    }

    this.layout = new Layout(targetEl, this.resourceResolver);
    this.layout.layoutListeners.add(this);
    if (this.layoutState) {
      this.restoreState(this.layoutState).then(() => {
        this.layout.layoutStateListeners.add(this);
      });
    } else {
      this.layout.layoutStateListeners.add(this);
    }
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
    return this.yamcsService.yamcsClient.getStaticText(path);
  }

  retrieveXML(path: string) {
    return this.yamcsService.yamcsClient.getStaticXML(path);
  }

  retrieveXMLDisplayResource(path: string) {
    const instance = this.yamcsService.getSelectedInstance().instance;
    const displayPath = `${instance}/displays/${path}`;
    return this.yamcsService.yamcsClient.getStaticXML(displayPath);
  }
}
