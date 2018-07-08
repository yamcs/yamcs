import { ChangeDetectionStrategy, Component, ElementRef, EventEmitter, Input, OnDestroy, OnInit, Output, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { ListObjectsOptions } from '@yamcs/client';
import { DisplayCommunicator } from '@yamcs/displays';
import { BehaviorSubject } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';
import { MyDisplayCommunicator } from '../displays/MyDisplayCommunicator';
import { DisplayFolder } from './DisplayFolder';
import { Coordinates, DisplayFrame } from './DisplayFrame';
import { FrameState, LayoutState } from './LayoutState';

@Component({
  selector: 'app-layout',
  templateUrl: './Layout.html',
  styleUrls: ['./Layout.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Layout implements OnInit, OnDestroy {

  @ViewChild('wrapper')
  public wrapperRef: ElementRef;

  @Input()
  startWithOpenedNavigator = true;

  @Input()
  layoutState: LayoutState = { frames: [] };

  @Output()
  stateChange = new EventEmitter<LayoutState>();

  @ViewChild('scrollPane')
  private scrollPaneRef: ElementRef;

  showNavigator$: BehaviorSubject<boolean>;
  currentFolder$ = new BehaviorSubject<DisplayFolder | null>(null);

  // Sorted by depth (back -> front)
  frames: DisplayFrame[] = [];

  framesById = new Map<string, DisplayFrame>();

  private synchronizer: number;

  /**
   * Limit client-side update to this amount of milliseconds.
   */
  private updateRate = 500;

  readonly displayCommunicator: DisplayCommunicator;

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

    this.synchronizer = window.setInterval(() => {
      for (const frame of this.frames) {
        frame.syncDisplay();
      }
    }, this.updateRate);

    if (this.layoutState) {
      const openPromises = [];
      for (const frameState of this.layoutState.frames) {
        openPromises.push(this.openDisplay(frameState.id, {
          x: frameState.x,
          y: frameState.y,
          width: frameState.width,
          height: frameState.height,
        }));
      }
      return Promise.all(openPromises);
    }
  }

  openDisplay(id: string, coordinates?: Coordinates): Promise<void> {
    const existingFrame = this.getDisplayFrame(id);
    if (existingFrame) {
      this.bringToFront(existingFrame);
    } else {
      return this.createDisplayFrame(id, coordinates);
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

  toggleNavigator() {
    this.showNavigator$.next(!this.showNavigator$.getValue());
  }

  createDisplayFrame(id: string, coordinates: Coordinates = { x: 20, y: 20 }) {
    if (this.framesById.has(id)) {
      throw new Error(`Layout already contains a frame with id ${id}`);
    }
    const targetEl = this.scrollPaneRef.nativeElement;
    const frame = new DisplayFrame(id, targetEl, this, coordinates);
    this.frames.push(frame);
    this.framesById.set(id, frame);
    return frame.loadAsync().then(() => {
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
      this.fireStateChange();
    });
  }

  /**
   * Repositions frames so they stack from top-left to bottom-right
   * in their current visibility order.
   */
  cascadeFrames() {
    let pos = 20;
    for (const frame of this.frames) {
      frame.setPosition(pos, pos);
      pos += 20;
    }
    this.fireStateChange();
  }

  /**
   * Fits all frames in a grid that fits in the available width/height.
   * Frames are scaled as needed.
   */
  tileFrames() {
    // Determine grid size
    const sqrt = Math.floor(Math.sqrt(this.frames.length));
    let rows = sqrt;
    let cols = sqrt;
    if (rows * cols < this.frames.length) {
      cols++;
      if (rows * cols < this.frames.length) {
        rows++;
      }
    }

    const gutter = 20;
    // clientWidth excludes size of scrollbars
    const targetEl = this.scrollPaneRef.nativeElement;
    const w = (targetEl.clientWidth - gutter - (cols * gutter)) / cols;
    const h = (targetEl.clientHeight - gutter - (rows * gutter)) / rows;
    let x = gutter;
    let y = gutter;
    for (let i = 0; i < rows; i++) {
      for (let j = 0; j < cols && ((i * cols) + j < this.frames.length); j++) {
        const frame = this.frames[(i * cols) + j];
        frame.setPosition(x, y);
        frame.setDimension(w, h - frame.titleBarHeight);
        x += w + gutter;
      }
      y += h + gutter;
      x = gutter;
    }
    this.fireStateChange();
  }

  hasDisplayFrame(id: string) {
    return this.framesById.has(id);
  }

  getDisplayFrame(id: string) {
    return this.framesById.get(id);
  }

  fireStateChange() {
    const state = this.getLayoutState();
    this.stateChange.emit(state);
  }

  clear() {
    const framesCopy = [...this.frames];
    for (const frame of framesCopy) {
      this.closeDisplayFrame(frame);
    }
  }

  /**
   * Returns a JSON structure describing the current layout contents
   */
  getLayoutState(): LayoutState {
    const frameStates: FrameState[] = [];
    for (const frame of this.frames) { // Keep front-to-back order
      frameStates.push({ id: frame.getBaseId(), ...frame.getCoordinates() });
    }
    return { frames: frameStates };
  }

  closeDisplayFrame(frame: DisplayFrame) {
    // TODO unsubscribe
    const idx = this.frames.indexOf(frame);
    this.frames.splice(idx, 1);
    this.framesById.forEach((other, id) => {
      if (frame === other) {
        this.framesById.delete(id);
      }
    });
    const targetEl = this.scrollPaneRef.nativeElement;
    const frameEl = targetEl.children[idx];
    targetEl.removeChild(frameEl);
    this.fireStateChange();
  }

  bringToFront(frame: DisplayFrame) {
    const idx = this.frames.indexOf(frame);
    const targetEl = this.scrollPaneRef.nativeElement;
    if (0 <= idx && idx < this.frames.length - 1) {
      this.frames.push(this.frames.splice(idx, 1)[0]);
      const frameEl = targetEl.children[idx];
      targetEl.appendChild(frameEl);
      this.fireStateChange();
    }
  }

  ngOnDestroy() {
    window.clearInterval(this.synchronizer);
  }
}
