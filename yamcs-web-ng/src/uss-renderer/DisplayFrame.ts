import { ResourceResolver } from './ResourceResolver';
import { Display } from './Display';
import { ParameterUpdate } from './ParameterUpdate';

export class DisplayFrame {

  container: HTMLDivElement;
  titleBar: HTMLDivElement;
  frameContent: HTMLDivElement;

  titleBarHeight = '20px';

  display: Display;

  constructor(
    private targetEl: HTMLDivElement,
    private resourceResolver: ResourceResolver,
    private xmlDoc: XMLDocument) {

    this.container = document.createElement('div');
    this.container.style.setProperty('position', 'absolute');
    this.container.style.setProperty('top', '0');
    this.container.style.setProperty('left', '0');
    this.container.style.setProperty('background-color', '#e9e9e9');
    this.container.style.setProperty('border-top-left-radius', '5px');
    this.container.style.setProperty('border-top-right-radius', '5px');

    this.titleBar = document.createElement('div');
    this.titleBar.textContent = 'Loading...';
    this.titleBar.style.setProperty('text-align', 'center');
    this.titleBar.style.setProperty('height', this.titleBarHeight);
    this.titleBar.style.setProperty('line-height', this.titleBarHeight);
    this.titleBar.style.setProperty('cursor', 'move');
    this.titleBar.style.setProperty('user-select', 'none');
    this.titleBar.style.setProperty('-moz-user-select', 'none');
    this.titleBar.style.setProperty('-khtml-user-select', 'none');
    this.titleBar.style.setProperty('-webkit-user-select', 'none');
    this.container.appendChild(this.titleBar);

    this.frameContent = document.createElement('div');
    this.frameContent.style.setProperty('position', 'absolute');
    this.frameContent.style.setProperty('top', this.titleBarHeight);
    this.container.appendChild(this.frameContent);

    this.setPosition(20, 20);
    this.targetEl.appendChild(this.container);

    this.renderDisplay();
    this.addEventHandlers();
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

  private addEventHandlers() {
    let startX: number;
    let startY: number;
    let pageStartX: number;
    let pageStartY: number;
    let mousedown = false;

    const mouseMoveHandler = (evt: MouseEvent) => {
      if (mousedown) {
        const dx = evt.pageX - pageStartX;
        const dy = evt.pageY - pageStartY;
        const x = Math.max(0, startX + dx);
        const y = Math.max(0, startY + dy);
        this.setPosition(x, y);
      }
    };
    const mouseUpHandler = (evt: MouseEvent) => {
      document.removeEventListener('mousemove', mouseMoveHandler);
      document.removeEventListener('mouseup', mouseUpHandler);
      mousedown = false;
    };

    this.titleBar.addEventListener('mousedown', evt => {
      startX = parseInt(this.container.style.getPropertyValue('left'), 10);
      startY = parseInt(this.container.style.getPropertyValue('top'), 10);
      pageStartX = evt.pageX;
      pageStartY = evt.pageY;
      mousedown = true;
      document.addEventListener('mousemove', mouseMoveHandler);
      document.addEventListener('mouseup', mouseUpHandler);
    });
  }

  private renderDisplay() {
    this.display = new Display(this.frameContent, this.resourceResolver);
    this.display.parseAndDraw(this.xmlDoc);

    this.container.style.setProperty('width', `${this.display.width}px`);
    this.titleBar.textContent = this.display.title;
  }
}
