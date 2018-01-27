import { ResourceResolver } from './ResourceResolver';
import { DisplayFrame } from './DisplayFrame';

export class Layout {

  scrollPane: HTMLDivElement;

  // Sorted by depth (back -> front)
  frames: DisplayFrame[] = [];

  constructor(
    private targetEl: HTMLDivElement,
    readonly resourceResolver: ResourceResolver,
  ) {
    this.scrollPane = document.createElement('div');
    this.scrollPane.style.setProperty('position', 'absolute');
    this.scrollPane.style.setProperty('width', '100%');
    this.scrollPane.style.setProperty('height', '100%');
    this.scrollPane.style.setProperty('overflow', 'scroll');
    this.targetEl.appendChild(this.scrollPane);
  }

  openDisplay(doc: XMLDocument) {
    const frame = new DisplayFrame(this.scrollPane, this, doc);
    this.frames.push(frame);
    return frame;
  }

  bringToFront(frame: DisplayFrame) {
    const idx = this.frames.indexOf(frame);
    if (idx >= 0) {
      this.frames.push(this.frames.splice(idx, 1)[0]);
      const frameEl = this.scrollPane.children[idx];
      // this.scrollPane.removeChild(frameEl);
      this.scrollPane.appendChild(frameEl);
    }
  }
}
