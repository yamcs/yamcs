import { Display } from './Display';
import { ParameterUpdate } from './ParameterUpdate';
import { Layout } from './Layout';

export class DisplayFrame {

  container: HTMLDivElement;
  titleBar: HTMLDivElement;
  frameContent: HTMLDivElement;
  resizeHandle: HTMLDivElement;

  titleBarHeight = 20;

  display: Display;

  constructor(
    private targetEl: HTMLDivElement,
    readonly layout: Layout,
    private xmlDoc: XMLDocument) {

    this.container = document.createElement('div');
    this.container.style.setProperty('position', 'absolute');
    this.container.style.setProperty('top', '0');
    this.container.style.setProperty('left', '0');
    this.container.style.setProperty('box-sizing', 'content-box');
    this.container.style.setProperty('box-shadow', '0 2px 4px 0 rgba(0, 0, 0, 0.16), 0 2px 10px 0 rgba(0, 0, 0, 0.12)');

    this.titleBar = document.createElement('div');
    this.titleBar.textContent = 'Loading...';
    this.titleBar.style.setProperty('background-color', '#e9e9e9');
    this.titleBar.style.setProperty('text-align', 'center');
    this.titleBar.style.setProperty('height', `${this.titleBarHeight}px`);
    this.titleBar.style.setProperty('line-height', `${this.titleBarHeight}px`);
    this.titleBar.style.setProperty('cursor', 'move');
    this.titleBar.style.setProperty('user-select', 'none');
    this.titleBar.style.setProperty('-moz-user-select', 'none');
    this.titleBar.style.setProperty('-khtml-user-select', 'none');
    this.titleBar.style.setProperty('-webkit-user-select', 'none');
    this.container.appendChild(this.titleBar);

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

    this.setPosition(20, 20);
    this.targetEl.appendChild(this.container);

    this.renderDisplay();
    this.addEventHandlers();
  }

  setDimension(width: number, height: number) {
    const xRatio = width / this.display.width;
    const yRatio = height / this.display.height;
    const zoom = Math.min(xRatio, yRatio);
    this.container.style.setProperty('width', `${width}px`);
    this.container.style.setProperty('height', `${height + this.titleBarHeight}px`);
    this.frameContent.style.setProperty('zoom', String(zoom));
    this.frameContent.style.setProperty('-moz-transform', `scale(${zoom})`);
  }

  setPosition(x: number, y: number) {
    this.container.style.setProperty('left', `${x}px`);
    this.container.style.setProperty('top', `${y}px`);
  }

  getOpsNames() {
    return this.display.getOpsNames();
  }

  updateExternalDataSources(updates: ParameterUpdate[]) {
    this.display.updateWidgets(updates);
  }

  private renderDisplay() {
    this.display = new Display(this.frameContent, this.layout.resourceResolver);
    this.display.frame = this;
    this.display.parseAndDraw(this.xmlDoc);

    this.container.style.setProperty('background-color', this.display.bgcolor.toString());
    this.setDimension(this.display.width, this.display.height);
    this.titleBar.textContent = this.display.title;
  }

  private addEventHandlers() {
    this.addTitleBarEventHandlers();
    this.addResizeEventHandlers();

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
