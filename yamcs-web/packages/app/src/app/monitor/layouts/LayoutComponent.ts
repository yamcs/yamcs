import { ChangeDetectionStrategy, Component, ElementRef, EventEmitter, Input, OnChanges, OnInit, Output, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { ListObjectsOptions } from '@yamcs/client';
import { DisplayCommunicator } from '@yamcs/displays';
import { BehaviorSubject } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';
import { MyDisplayCommunicator } from '../displays/MyDisplayCommunicator';
import { DisplayFolder } from './DisplayFolder';
import { Coordinates, DisplayFrame } from './DisplayFrame';
import { Layout, LayoutListener, LayoutStateListener } from './Layout';
import { LayoutState } from './LayoutState';

@Component({
  selector: 'app-layout-component',
  templateUrl: './LayoutComponent.html',
  styleUrls: ['./LayoutComponent.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LayoutComponent implements OnInit, OnChanges, LayoutListener, LayoutStateListener {

  @ViewChild('wrapper')
  public wrapperRef: ElementRef;

  @Input()
  startWithOpenedNavigator = true;

  @Input()
  layoutState: LayoutState = { frames: [] };

  @Output()
  stateChange = new EventEmitter<LayoutState>();

  @ViewChild('displayContainer')
  private displayContainerRef: ElementRef;

  showNavigator$: BehaviorSubject<boolean>;
  currentFolder$ = new BehaviorSubject<DisplayFolder | null>(null);

  private displayCommunicator: DisplayCommunicator;
  private layout: Layout;

  constructor(private yamcs: YamcsService, router: Router) {
    this.displayCommunicator = new MyDisplayCommunicator(yamcs, router);
  }

  ngOnInit() {
    this.showNavigator$ = new BehaviorSubject<boolean>(this.startWithOpenedNavigator);
    this.yamcs.getInstanceClient()!.listObjects('displays', {
      delimiter: '/',
    }).then(response => {
      this.currentFolder$.next({
        location: '',
        prefixes: response.prefix || [],
        objects: response.object || [],
      });
    });
  }

  ngOnChanges() {
    const targetEl = this.displayContainerRef.nativeElement;
    if (this.layout) {
      this.layout.layoutListeners.delete(this);
      this.layout.layoutStateListeners.delete(this);
      targetEl.innerHTML = '';
    }

    this.layout = new Layout(targetEl, this.displayCommunicator);
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

  prefixChange(path: string) {
    const options: ListObjectsOptions = {
      delimiter: '/',
    };
    if (path) {
      options.prefix = path;
    }
    this.yamcs.getInstanceClient()!.listObjects('displays', options).then(response => {
      this.currentFolder$.next({
        location: path,
        prefixes: response.prefix || [],
        objects: response.object || [],
      });
    });
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
      this.yamcs.getInstanceClient()!.getParameterValueUpdates({
        id: ids,
        abortOnInvalid: false,
        sendFromCache: true,
        updateOnExpiration: true,
      }).then(res => {
        res.parameterValues$.subscribe(pvals => {
          frame.processParameterValues(pvals);
        });
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
