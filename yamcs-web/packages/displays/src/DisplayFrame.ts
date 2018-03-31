import { Layout } from './Layout';
import { UssDisplay } from './uss/UssDisplay';
import { ParameterValue, NamedObjectId } from '@yamcs/client';
import { Display } from './Display';
import { OpiDisplay } from './opi/OpiDisplay';

export interface Coordinates {
  x: number;
  y: number;
  width?: number;
  height?: number;
}

export class DisplayFrame {

  container: HTMLDivElement;

  titleBar: HTMLDivElement;
  title: HTMLSpanElement;
  closeButton: Element;
  frameActions: HTMLDivElement;

  frameContent: HTMLDivElement;
  resizeHandle: HTMLDivElement;

  titleBarHeight = 20;

  display: Display;

  constructor(
    readonly id: string,
    private targetEl: HTMLDivElement,
    readonly layout: Layout,
    private coordinates: Coordinates) {

    this.container = document.createElement('div');
    this.container.style.setProperty('visibility', 'hidden');
    this.container.style.setProperty('position', 'absolute');
    this.container.style.setProperty('top', '0');
    this.container.style.setProperty('left', '0');
    this.container.style.setProperty('box-sizing', 'content-box');
    this.container.style.setProperty('box-shadow', '0 2px 4px 0 rgba(0, 0, 0, 0.16), 0 2px 10px 0 rgba(0, 0, 0, 0.12)');

    this.titleBar = document.createElement('div');
    this.titleBar.style.setProperty('background-color', '#e9e9e9');
    this.titleBar.style.setProperty('height', `${this.titleBarHeight}px`);
    this.titleBar.style.setProperty('line-height', `${this.titleBarHeight}px`);
    this.titleBar.style.setProperty('cursor', 'move');
    this.titleBar.style.setProperty('user-select', 'none');
    this.titleBar.style.setProperty('-moz-user-select', 'none');
    this.titleBar.style.setProperty('-khtml-user-select', 'none');
    this.titleBar.style.setProperty('-webkit-user-select', 'none');
    this.container.appendChild(this.titleBar);

    this.title = document.createElement('span');
    this.title.style.setProperty('padding-left', '0.5em');
    this.title.style.setProperty('class', 'mat-typography');
    this.title.style.setProperty('font-size', '12px');
    this.titleBar.appendChild(this.title);

    this.frameActions = document.createElement('div');
    this.frameActions.style.setProperty('position', 'absolute');
    this.frameActions.style.setProperty('top', '0');
    this.frameActions.style.setProperty('right', '0.5em');
    this.frameActions.style.setProperty('height', `${this.titleBarHeight}px`);
    this.frameActions.style.setProperty('line-height', `${this.titleBarHeight}px`);
    this.frameActions.style.setProperty('color', 'grey');
    this.frameActions.style.setProperty('cursor', 'pointer');
    this.frameActions.style.setProperty('user-select', 'none');
    this.frameActions.style.setProperty('-moz-user-select', 'none');
    this.frameActions.style.setProperty('-khtml-user-select', 'none');
    this.frameActions.style.setProperty('-webkit-user-select', 'none');
    this.container.appendChild(this.frameActions);

    this.closeButton = document.createElement('span');
    this.closeButton.textContent = '⨉';
    this.frameActions.appendChild(this.closeButton);

    this.frameContent = document.createElement('div');
    this.frameContent.style.setProperty('line-height', '0');
    this.container.appendChild(this.frameContent);

    this.resizeHandle = document.createElement('div');
    this.resizeHandle.style.setProperty('position', 'absolute');
    this.resizeHandle.style.setProperty('bottom', '0');
    this.resizeHandle.style.setProperty('right', '0');
    this.resizeHandle.style.setProperty('width', '10px');
    this.resizeHandle.style.setProperty('height', '10px');
    this.resizeHandle.style.setProperty('cursor', 'nwse-resize');
    this.container.appendChild(this.resizeHandle);

    this.targetEl.appendChild(this.container);

    if (id.toLowerCase().endsWith('uss')) {
      this.display = new UssDisplay(this, this.frameContent, this.layout.displayCommunicator);
    } else if (id.toLowerCase().endsWith('opi')) {
      this.display = new OpiDisplay(this, this.frameContent, this.layout.displayCommunicator);
    } else {
      alert('No viewer for file ' + id);
    }
  }

  async loadAsync() {
    return this.display.parseAndDraw(this.id).then(() => {
      this.container.style.setProperty('background-color', this.display.getBackgroundColor());
      this.setDimension(this.display.width, this.display.height);
      this.title.textContent = this.display.title;

      this.setPosition(this.coordinates.x, this.coordinates.y);
      if (this.coordinates.width !== undefined && this.coordinates.height !== undefined) {
        this.setDimension(this.coordinates.width, this.coordinates.height);
      }
      this.container.style.setProperty('visibility', 'visible');
      this.addEventHandlers();
    });
  }

  setDimension(width: number, height: number) {
    const xRatio = width / this.display.width;
    const yRatio = height / this.display.height;
    const zoom = Math.min(xRatio, yRatio);
    this.container.style.setProperty('width', `${width}px`);
    this.container.style.setProperty('height', `${height + this.titleBarHeight}px`);
    this.frameContent.style.setProperty('zoom', String(zoom));

    // Zoom does not work in FF
    this.frameContent.style.setProperty('-moz-transform', `scale(${zoom})`);
    this.frameContent.style.setProperty('-moz-transform-origin', '0px 0px');
  }

  setPosition(x: number, y: number) {
    this.container.style.setProperty('left', `${x}px`);
    this.container.style.setProperty('top', `${y}px`);
  }

  getCoordinates() {
    return {
      x: parseInt(this.container.style.getPropertyValue('left'), 10),
      y: parseInt(this.container.style.getPropertyValue('top'), 10),
      width: this.container.getBoundingClientRect().width,
      height: this.container.getBoundingClientRect().height - this.titleBarHeight,
    };
  }

  getParameterIds(): NamedObjectId[] {
    return this.display.getParameterIds();
  }

  syncDisplay() {
    const state = this.display.getDataSourceState();
    if (state.red) {
      this.titleBar.style.setProperty('background-color', 'red');
      this.titleBar.style.setProperty('color', 'white');
      this.frameActions.style.setProperty('color', 'white');
    } else if (state.yellow) {
      this.titleBar.style.setProperty('background-color', 'yellow');
      this.titleBar.style.setProperty('color', 'black');
      this.frameActions.style.setProperty('color', 'black');
    } else if (state.green) {
      this.titleBar.style.setProperty('background-color', 'green');
      this.titleBar.style.setProperty('color', 'white');
      this.frameActions.style.setProperty('color', 'white');
    } else {
      this.titleBar.style.setProperty('background-color', '#e9e9e9');
      this.titleBar.style.setProperty('color', 'black');
      this.frameActions.style.setProperty('color', 'grey');
    }
    this.display.digest();
  }

  processParameterValues(pvals: ParameterValue[]) {
    this.display.processParameterValues(pvals);
  }

  private addEventHandlers() {
    this.addTitleBarEventHandlers();
    this.addResizeEventHandlers();

    this.closeButton.addEventListener('click', evt => {
      this.layout.closeDisplayFrame(this);
    });

    // Brings the current frame to the front upon click event - with
    // the exception of click that come from controls like NavigationButtons
    // (to prevent collisions).
    this.frameContent.addEventListener('click', evt => {
      const target = evt.target as Element;
      if (target.getAttribute('data-control') !== 'true') {
        this.layout.bringToFront(this);
      }
    });
  }

  /**
   * Allows moving a frame around by clicking and holding
   * the mouse on the titlebar.
   */
  private addTitleBarEventHandlers() {
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

    this.titleBar.addEventListener('mousedown', evt => {
      evt.preventDefault();
      this.layout.bringToFront(this);
      startX = parseInt(this.container.style.getPropertyValue('left'), 10);
      startY = parseInt(this.container.style.getPropertyValue('top'), 10);
      pageStartX = evt.pageX;
      pageStartY = evt.pageY;
      mousedown = true;
      document.addEventListener('mousemove', mouseMoveHandler);
      document.addEventListener('mouseup', mouseUpHandler);
    });
  }

  /*
   * Detect via mouse events whether the frame should be scaled.
   * Would prefer to use 'resize: true' but there's not yet a
   * robust cross-browser way to capture the width/height
   * changes of a specific div.
   */
  private addResizeEventHandlers() {
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
    this.resizeHandle.addEventListener('mousedown', evt => {
      evt.preventDefault();
      this.layout.bringToFront(this);
      startWidth = parseInt(this.container.style.getPropertyValue('width'), 10);
      startHeight = parseInt(this.container.style.getPropertyValue('height'), 10) - this.titleBarHeight;
      pageStartX = evt.pageX;
      pageStartY = evt.pageY;
      mousedown = true;
      document.addEventListener('mousemove', mouseMoveHandler);
      document.addEventListener('mouseup', mouseUpHandler);
    });
  }
}
