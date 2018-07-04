import { ChangeDetectionStrategy, Component, ElementRef, EventEmitter, Input, OnChanges, OnInit, Output, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { DisplayFolder } from '@yamcs/client';
import { Coordinates, DisplayCommunicator, DisplayFrame, Layout, LayoutListener, LayoutState, LayoutStateListener } from '@yamcs/displays';
import { BehaviorSubject } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';
import { MyDisplayCommunicator } from '../displays/MyDisplayCommunicator';

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
    this.yamcs.getInstanceClient()!.getDisplayFolder().then(folder => {
      this.currentFolder$.next(folder);
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

  pathChange(path: string) {
    this.yamcs.getInstanceClient()!.getDisplayFolder(path).then(folder => {
      this.currentFolder$.next(folder);
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
