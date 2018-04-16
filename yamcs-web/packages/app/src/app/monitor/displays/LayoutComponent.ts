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
import {
  Coordinates,
  DisplayCommunicator,
  DisplayFrame,
  Layout,
  LayoutListener,
  LayoutStateListener,
  LayoutState,
} from '@yamcs/displays';
import { YamcsService } from '../../core/services/YamcsService';
import { DisplayFolder } from '@yamcs/client';
import { BehaviorSubject } from 'rxjs/BehaviorSubject';
import { MyDisplayCommunicator } from './MyDisplayCommunicator';
import { Router } from '@angular/router';

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

  private displayCommunicator: DisplayCommunicator;
  private layout: Layout;

  constructor(private yamcs: YamcsService, router: Router) {
    this.yamcs.getInstanceClient()!.getDisplayInfo().then(displayInfo => {
      this.displayInfo$.next(displayInfo);
    });
    this.displayCommunicator = new MyDisplayCommunicator(yamcs, router);
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
