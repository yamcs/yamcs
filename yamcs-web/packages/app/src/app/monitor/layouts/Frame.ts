import { ChangeDetectorRef, Component, ElementRef, ViewChild } from '@angular/core';
import { NamedObjectId, ParameterValue } from '@yamcs/client';
import { Display, DisplayHolder, OpenDisplayCommandOptions, OpiDisplay, UssDisplay } from '@yamcs/displays';
import { Layout } from './Layout';

export interface Coordinates {
  x: number;
  y: number;
  width?: number;
  height?: number;
}

/**
 * A frame shows a display inside a layout
 */
@Component({
  selector: 'app-frame',
  templateUrl: './Frame.html',
  styleUrls: ['./Frame.css'],
})
export class Frame implements DisplayHolder {

  @ViewChild('container')
  private containerRef: ElementRef;

  @ViewChild('titlebar')
  private titlebarRef: ElementRef;

  @ViewChild('frameActions')
  private frameActionsRef: ElementRef;

  @ViewChild('frameContent')
  private frameContentRef: ElementRef;

  private id: string;
  private layout: Layout;
  private coordinates: Coordinates;

  display: Display;

  readonly titleBarHeight = 20;

  constructor(private changeDetector: ChangeDetectorRef) {

  }

  public init(id: string, layout: Layout, coordinates: Coordinates) {
    this.id = id;
    this.layout = layout;
    this.coordinates = coordinates;

    const targetEl = this.frameContentRef.nativeElement;
    if (id.toLowerCase().endsWith('uss')) {
      this.display = new UssDisplay(this, targetEl, this.layout.displayCommunicator);
    } else if (id.toLowerCase().endsWith('opi')) {
      this.display = new OpiDisplay(this, targetEl, this.layout.displayCommunicator);
    } else {
      alert('No viewer for file ' + id);
    }
  }

  async loadAsync() {
    return this.display.parseAndDraw(this.id).then(() => {
      const container = this.containerRef.nativeElement as HTMLDivElement;
      container.style.backgroundColor = this.display.getBackgroundColor();
      this.setDimension(this.display.width, this.display.height);

      this.setPosition(this.coordinates.x, this.coordinates.y);
      if (this.coordinates.width !== undefined && this.coordinates.height !== undefined) {
        this.setDimension(this.coordinates.width, this.coordinates.height);
      }
      container.style.visibility = 'visible';

      this.changeDetector.detectChanges();
    });
  }

  setDimension(width: number, height: number) {
    const xRatio = width / this.display.width;
    const yRatio = height / this.display.height;
    const zoom = Math.min(xRatio, yRatio);

    const container = this.containerRef.nativeElement as HTMLDivElement;
    container.style.width = `${width}px`;
    container.style.height = `${height + this.titleBarHeight}px`;

    const frameContent = this.frameContentRef.nativeElement as HTMLDivElement;
    frameContent.style.zoom = String(zoom);

    // Zoom does not work in FF
    frameContent.style.setProperty('-moz-transform', `scale(${zoom})`);
    frameContent.style.setProperty('-moz-transform-origin', '0px 0px');
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

  getParameterIds(): NamedObjectId[] {
    return this.display.getParameterIds();
  }

  syncDisplay() {
    const state = this.display.getDataSourceState();
    const titlebar = this.titlebarRef.nativeElement as HTMLDivElement;
    const frameActions = this.frameActionsRef.nativeElement as HTMLDivElement;
    if (state.red) {
      titlebar.style.setProperty('background-color', 'red');
      titlebar.style.setProperty('color', 'white');
      frameActions.style.setProperty('color', 'white');
    } else if (state.yellow) {
      titlebar.style.setProperty('background-color', 'yellow');
      titlebar.style.setProperty('color', 'black');
      frameActions.style.setProperty('color', 'black');
    } else if (state.green) {
      titlebar.style.setProperty('background-color', 'green');
      titlebar.style.setProperty('color', 'white');
      frameActions.style.setProperty('color', 'white');
    } else {
      titlebar.style.setProperty('background-color', '#e9e9e9');
      titlebar.style.setProperty('color', 'black');
      frameActions.style.setProperty('color', 'grey');
    }
    this.display.digest();
  }

  processParameterValues(pvals: ParameterValue[]) {
    this.display.processParameterValues(pvals);
  }

  /**
   * Brings the current frame to the front upon click event.
   * With the exception of clicks that come from controls like
   * NavigationButtons (to prevent collisions).
   */
  onFrameContentClick($event: Event) {
    const target = $event.target as Element;
    if (target.getAttribute('data-control') !== 'true') {
      this.layout.bringToFront(this.id);
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
    this.layout.bringToFront(this.id);
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
    this.layout.bringToFront(this.id);
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
      this.layout.bringToFront(alreadyOpenFrame.id);
    } else {
      if (!options.openInNewWindow) {
        this.layout.closeDisplayFrame(this.id);
      }
      this.layout.createDisplayFrame(options.target, options.coordinates);
    }
  }

  closeDisplay() {
    this.layout.closeDisplayFrame(this.id);
  }
}
