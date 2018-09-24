import { ChangeDetectorRef, Component, ComponentFactoryResolver, ElementRef, Type, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { Display, DisplayCommunicator, NavigationHandler, OpenDisplayCommandOptions } from '@yamcs/displays';
import { BehaviorSubject } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';
import { MyDisplayCommunicator } from '../displays/MyDisplayCommunicator';
import { OpiDisplayViewer } from '../displays/OpiDisplayViewer';
import { ParameterTableViewer } from '../displays/ParameterTableViewer';
import { UssDisplayViewer } from '../displays/UssDisplayViewer';
import { Viewer } from '../displays/Viewer';
import { ViewerHost } from '../displays/ViewerHost';
import { Layout } from './Layout';

export interface Coordinates {
  x: number;
  y: number;
  width?: number;
  height?: number;
}

type DisplayType = 'OPI' | 'PAR' | 'USS';

/**
 * A frame shows a display inside a layout
 */
@Component({
  selector: 'app-frame',
  templateUrl: './Frame.html',
  styleUrls: ['./Frame.css'],
})
export class Frame implements NavigationHandler {

  @ViewChild('container')
  private containerRef: ElementRef;

  @ViewChild('titlebar')
  private titlebarRef: ElementRef;

  @ViewChild('frameActions')
  private frameActionsRef: ElementRef;

  @ViewChild('frameContent')
  private frameContentRef: ElementRef;

  @ViewChild(ViewerHost)
  private viewerHost: ViewerHost;

  id: string;
  private layout: Layout;
  private coordinates: Coordinates;

  display: Display;

  private preferredWidth = 800;
  private preferredHeight = 400;
  zoomToFit$ = new BehaviorSubject<boolean>(false);

  readonly titleBarHeight = 20;

  readonly displayCommunicator: DisplayCommunicator;

  constructor(
    private componentFactoryResolver: ComponentFactoryResolver,
    private changeDetector: ChangeDetectorRef,
    yamcs: YamcsService,
    router: Router,
  ) {
    this.displayCommunicator = new MyDisplayCommunicator(yamcs, router);
  }

  public init(id: string, layout: Layout, coordinates: Coordinates) {
    this.id = id;
    this.layout = layout;
    this.coordinates = coordinates;

    const container = this.containerRef.nativeElement as HTMLDivElement;
    const displayType = this.getDisplayType();
    let initPromise;
    if (displayType === 'USS') {
      const viewer = this.createViewer(UssDisplayViewer);
      initPromise = viewer.init(id, this).then(() => {
        this.display = viewer.display;
        container.style.backgroundColor = viewer.display.getBackgroundColor();
        this.preferredWidth = viewer.display.width;
        this.preferredHeight = viewer.display.height;
        this.zoomToFit$.next(true);
      });
    } else if (displayType === 'OPI') {
      const viewer = this.createViewer(OpiDisplayViewer);
      initPromise = viewer.init(id).then(() => {
        this.display = viewer.display;
        container.style.backgroundColor = viewer.display.getBackgroundColor();
        this.preferredWidth = viewer.display.width;
        this.preferredHeight = viewer.display.height;
        this.zoomToFit$.next(true);
      });
    } else if (displayType === 'PAR') {
      container.style.backgroundColor = 'white';
      const viewer = this.createViewer(ParameterTableViewer);
      initPromise = viewer.init(id);
    } else {
      alert('No viewer for file ' + id);
      return Promise.resolve();
    }

    return initPromise.then(() => {
      this.setDimension(this.preferredWidth, this.preferredHeight);
      this.setPosition(this.coordinates.x, this.coordinates.y);
      if (this.coordinates.width !== undefined && this.coordinates.height !== undefined) {
        this.setDimension(this.coordinates.width, this.coordinates.height);
      }
      container.style.visibility = 'visible';
      this.changeDetector.detectChanges();
    });
  }

  private getDisplayType(): DisplayType | undefined {
    if (this.id.toLowerCase().endsWith('.uss')) {
      return 'USS';
    } else if (this.id.toLowerCase().endsWith('.opi')) {
      return 'OPI';
    } else if (this.id.toLowerCase().endsWith('.par')) {
      return 'PAR';
    }
  }

  setDimension(width: number, height: number) {
    const xRatio = width / this.preferredWidth;
    const yRatio = height / this.preferredHeight;
    const zoom = Math.min(xRatio, yRatio);

    const container = this.containerRef.nativeElement as HTMLDivElement;
    container.style.width = `${width}px`;
    container.style.height = `${height + this.titleBarHeight}px`;

    if (this.zoomToFit$.value) {
      const frameContent = this.frameContentRef.nativeElement as HTMLDivElement;
      frameContent.style.setProperty('transform', `scale(${zoom})`);
      frameContent.style.setProperty('transform-origin', '50% 50%');
    }
  }

  setPosition(x: number, y: number) {
    const container = this.containerRef.nativeElement as HTMLDivElement;
    container.style.left = `${x}px`;
    container.style.top = `${y}px`;
  }

  getCoordinates() {
    const container = this.containerRef.nativeElement as HTMLDivElement;
    return {
      x: parseInt(container.style.left!, 10),
      y: parseInt(container.style.top!, 10),
      width: container.getBoundingClientRect().width,
      height: container.getBoundingClientRect().height - this.titleBarHeight,
    };
  }

  syncDisplay() {
    if (!this.display) {
      return;
    }
    const state = this.display.getDataSourceState();
    const titlebar = this.titlebarRef.nativeElement as HTMLDivElement;
    const frameActions = this.frameActionsRef.nativeElement as HTMLDivElement;
    if (state.red) {
      titlebar.style.backgroundColor = 'red';
      titlebar.style.color = 'white';
      frameActions.style.color = 'white';
    } else if (state.yellow) {
      titlebar.style.backgroundColor = 'yellow';
      titlebar.style.color = 'black';
      frameActions.style.color = 'black';
    } else if (state.green) {
      titlebar.style.backgroundColor = 'green';
      titlebar.style.color = 'white';
      frameActions.style.color = 'white';
    } else {
      titlebar.style.backgroundColor = '#e9e9e9';
      titlebar.style.color = 'black';
      frameActions.style.color = 'grey';
    }
    this.display.digest();
  }

  /**
   * Brings the current frame to the front upon click event.
   * With the exception of clicks that come from controls like
   * NavigationButtons (to prevent collisions).
   */
  onFrameContentClick($event: Event) {
    const target = $event.target as Element;
    if (target.getAttribute('data-control') !== 'true') {
      this.layout.bringToFront(this.id, true);
    }
  }

  closeFrame() {
    this.layout.closeDisplayFrame(this.id);
  }

  /**
   * Allows moving a frame around by clicking and holding
   * the mouse on the titlebar.
   */
  onTitlebarMouseDown($event: MouseEvent) {
    let startX: number;
    let startY: number;
    let pageStartX: number;
    let pageStartY: number;
    let mousedown = false;

    const mouseMoveHandler = (evt: MouseEvent) => {
      evt.preventDefault();
      if (mousedown) {
        const dx = evt.pageX - pageStartX;
        const dy = evt.pageY - pageStartY;
        const x = Math.max(0, startX + dx);
        const y = Math.max(0, startY + dy);
        this.setPosition(x, y);
      }
    };
    const mouseUpHandler = (evt: MouseEvent) => {
      evt.preventDefault();
      document.removeEventListener('mousemove', mouseMoveHandler);
      document.removeEventListener('mouseup', mouseUpHandler);
      mousedown = false;
      this.layout.fireStateChange();
    };

    $event.preventDefault();
    this.layout.bringToFront(this.id, true);
    const container = this.containerRef.nativeElement as HTMLDivElement;
    startX = parseInt(container.style.left!, 10);
    startY = parseInt(container.style.top!, 10);
    pageStartX = $event.pageX;
    pageStartY = $event.pageY;
    mousedown = true;
    document.addEventListener('mousemove', mouseMoveHandler);
    document.addEventListener('mouseup', mouseUpHandler);
  }

  /*
   * Detect via mouse events whether the frame should be scaled.
   * Would prefer to use 'resize: true' but there's not yet a
   * robust cross-browser way to capture the width/height
   * changes of a specific div.
   */
  onResizeHandleMouseDown($event: MouseEvent) {
    let startWidth: number;
    let startHeight: number;
    let pageStartX: number;
    let pageStartY: number;
    let mousedown = false;
    const mouseMoveHandler = (evt: MouseEvent) => {
      evt.preventDefault();
      if (mousedown) {
        const dx = evt.pageX - pageStartX;
        const dy = evt.pageY - pageStartY;
        const width = Math.max(50, startWidth + dx);
        const height = Math.max(50, startHeight + dy);
        this.setDimension(width, height);
      }
    };
    const mouseUpHandler = (evt: MouseEvent) => {
      evt.preventDefault();
      document.removeEventListener('mousemove', mouseMoveHandler);
      document.removeEventListener('mouseup', mouseUpHandler);
      mousedown = false;
      this.layout.fireStateChange();
    };

    $event.preventDefault();
    this.layout.bringToFront(this.id, true);
    const container = this.containerRef.nativeElement as HTMLDivElement;
    startWidth = parseInt(container.style.width!, 10);
    startHeight = parseInt(container.style.height!, 10) - this.titleBarHeight;
    pageStartX = $event.pageX;
    pageStartY = $event.pageY;
    mousedown = true;
    document.addEventListener('mousemove', mouseMoveHandler);
    document.addEventListener('mouseup', mouseUpHandler);
  }

  getBaseId() {
    return this.id;
  }

  openDisplay(options: OpenDisplayCommandOptions) {
    const alreadyOpenFrame = this.layout.getDisplayFrame(options.target);
    if (alreadyOpenFrame) {
      this.layout.bringToFront(alreadyOpenFrame.id, true);
    } else {
      if (!options.openInNewWindow) {
        this.layout.closeDisplayFrame(this.id);
      }
      this.layout.createDisplayFrame(options.target, true, options.coordinates);
    }
  }

  closeDisplay() {
    this.layout.closeDisplayFrame(this.id);
  }

  private createViewer<T extends Viewer>(viewer: Type<T>): T {
    const componentFactory = this.componentFactoryResolver.resolveComponentFactory(viewer);
    const viewContainerRef = this.viewerHost.viewContainerRef;
    viewContainerRef.clear();
    const componentRef = viewContainerRef.createComponent(componentFactory);
    return componentRef.instance;
  }
}
