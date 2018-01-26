import { ResourceResolver } from './ResourceResolver';
import { DisplayFrame } from './DisplayFrame';

export class Layout {

  scrollPane: HTMLDivElement;

  constructor(
    private targetEl: HTMLDivElement,
    private resourceResolver: ResourceResolver,
  ) {
    this.scrollPane = document.createElement('div');
    this.scrollPane.style.setProperty('position', 'absolute');
    this.scrollPane.style.setProperty('width', '100%');
    this.scrollPane.style.setProperty('height', '100%');
    // this.scrollPane.style.setProperty('background-color', 'green');
    this.scrollPane.style.setProperty('overflow', 'scroll');
    this.targetEl.appendChild(this.scrollPane);
  }

  openDisplay(doc: XMLDocument) {
    return new DisplayFrame(this.scrollPane, this.resourceResolver, doc);
  }
}
