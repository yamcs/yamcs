import { ChangeDetectionStrategy, Component, ComponentFactoryResolver, ComponentRef, ElementRef, EventEmitter, Input, OnDestroy, OnInit, Output, ViewChild, ViewRef } from '@angular/core';
import { ListObjectsOptions } from '@yamcs/client';
import { BehaviorSubject } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';
import { DisplayFolder } from './DisplayFolder';
import { Coordinates, Frame } from './Frame';
import { FrameHost } from './FrameHost';
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

  @ViewChild(FrameHost)
  private frameHost: FrameHost;

  showNavigator$: BehaviorSubject<boolean>;
  currentFolder$ = new BehaviorSubject<DisplayFolder | null>(null);

  private componentsById = new Map<string, ComponentRef<Frame>>();

  private synchronizer: number;

  /**
   * Limit client-side update to this amount of milliseconds.
   */
  private updateRate = 500;

  constructor(
    private yamcs: YamcsService,
    private componentFactoryResolver: ComponentFactoryResolver,
  ) {}

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
      this.componentsById.forEach(component => {
        component.instance.syncDisplay();
      });
    }, this.updateRate);

    if (this.layoutState) {
      const openPromises = [];
      for (const frameState of this.layoutState.frames) {
        openPromises.push(this.openDisplay(frameState.id, false, {
          x: frameState.x,
          y: frameState.y,
          width: frameState.width,
          height: frameState.height,
        }));
      }
      return Promise.all(openPromises);
    }
  }

  openDisplay(id: string, fireStateChange: boolean, coordinates?: Coordinates): Promise<void> {
    if (this.componentsById.has(id)) {
      this.bringToFront(id, fireStateChange);
    } else {
      return this.createDisplayFrame(id, fireStateChange, coordinates);
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

  createDisplayFrame(id: string, fireStateChange: boolean, coordinates: Coordinates = { x: 20, y: 20 }) {
    if (this.componentsById.has(id)) {
      throw new Error(`Layout already contains a frame with id ${id}`);
    }

    const componentFactory = this.componentFactoryResolver.resolveComponentFactory(Frame);
    const viewContainerRef = this.frameHost.viewContainerRef;
    const componentRef = viewContainerRef.createComponent(componentFactory);
    this.componentsById.set(id, componentRef);

    const frame = componentRef.instance;
    return frame.init(id, this, coordinates).then(() => {
      if (fireStateChange) {
        this.fireStateChange();
      }
    });
  }

  /**
   * Repositions frames so they stack from top-left to bottom-right
   * in their current visibility order.
   */
  cascadeFrames() {
    let pos = 20;
    for (const component of this.getOrderedComponents()) {
      component.instance.setPosition(pos, pos);
      pos += 20;
    }
    this.fireStateChange();
  }

  /**
   * Fits all frames in a grid that fits in the available width/height.
   * Frames are scaled as needed.
   */
  tileFrames() {
    const components = this.getOrderedComponents();

    // Determine grid size
    const len = components.length;
    const sqrt = Math.floor(Math.sqrt(len));
    let rows = sqrt;
    let cols = sqrt;
    if (rows * cols < len) {
      cols++;
      if (rows * cols < len) {
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
      for (let j = 0; j < cols && ((i * cols) + j < len); j++) {
        const componentRef = components[(i * cols) + j];
        const frame = componentRef.instance;
        frame.setPosition(x, y);
        frame.setDimension(w, h - frame.titleBarHeight);
        x += w + gutter;
      }
      y += h + gutter;
      x = gutter;
    }
    this.fireStateChange();
  }

  getDisplayFrame(id: string) {
    const component = this.componentsById.get(id);
    if (component) {
      return component.instance;
    }
  }

  fireStateChange() {
    const state = this.getLayoutState();
    this.stateChange.emit(state);
  }

  /**
   * Returns a JSON structure describing the current layout contents
   */
  getLayoutState(): LayoutState {
    const frameStates: FrameState[] = [];
    for (const componentRef of this.getOrderedComponents()) {
      const frame = componentRef.instance;
      frameStates.push({ id: frame.getBaseId(), ...frame.getCoordinates() });
    }
    return { frames: frameStates };
  }

  private getOrderedComponents() {
    // Order is defined by the viewContainer (back-to-front)
    const components: ComponentRef<Frame>[] = [];
    const viewContainerRef = this.frameHost.viewContainerRef;
    for (let i = 0; i < viewContainerRef.length; i++) {
      const viewRef = viewContainerRef.get(i)!;
      const componentRef = this.findComponentForViewRef(viewRef)!;
      components.push(componentRef);
    }
    return components;
  }

  private findComponentForViewRef(viewRef: ViewRef) {
    const viewContainerRef = this.frameHost.viewContainerRef;
    for (const component of Array.from(this.componentsById.values())) {
      // These viewRefs cannot be directly compared (different instanstiations)
      // So use their index in the viewContainer instead.
      const idx1 = viewContainerRef.indexOf(component.hostView);
      const idx2 = viewContainerRef.indexOf(viewRef);
      if (idx1 === idx2) {
        return component;
      }
    }
  }

  clear() {
    this.componentsById.clear();
    const viewContainerRef = this.frameHost.viewContainerRef;
    viewContainerRef.clear();
    this.fireStateChange();
  }

  closeDisplayFrame(id: string) {
    // TODO unsubscribe

    const component = this.componentsById.get(id);
    if (component) {
      const viewContainerRef = this.frameHost.viewContainerRef;
      const idx = viewContainerRef.indexOf(component.hostView);
      viewContainerRef.remove(idx);
      this.componentsById.delete(id);
    }

    this.fireStateChange();
  }

  bringToFront(id: string, fireStateChange: boolean) {
    const component = this.componentsById.get(id);
    if (component) {
      const viewContainerRef = this.frameHost.viewContainerRef;
      const idx = viewContainerRef.indexOf(component.hostView);
      viewContainerRef.detach(idx);
      viewContainerRef.insert(component.hostView);
      if (fireStateChange) {
        this.fireStateChange();
      }
    }
  }

  ngOnDestroy() {
    window.clearInterval(this.synchronizer);
  }
}
