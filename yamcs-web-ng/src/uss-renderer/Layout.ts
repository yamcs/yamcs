import { ResourceResolver } from './ResourceResolver';
import { DisplayFrame } from './DisplayFrame';

export class Layout {

  scrollPane: HTMLDivElement;

  // Sorted by depth (back -> front)
  frames: DisplayFrame[] = [];

  framesById = new Map<string, DisplayFrame>();

  synchronizer: number;

  /**
   * Limit client-side update to this amount of milliseconds.
   */
  private updateRate = 500;

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

    this.synchronizer = window.setInterval(() => {
      for (const frame of this.frames) {
        frame.syncDisplay();
      }
    }, this.updateRate);
  }

  createDisplayFrame(id: string, doc: XMLDocument) {
    if (this.framesById.has(id)) {
      throw new Error(`Layout already contains a frame with id ${id}`);
    }
    const frame = new DisplayFrame(this.scrollPane, this, doc);
    this.frames.push(frame);
    this.framesById.set(id, frame);
    return frame;
  }

  getDisplayFrame(id: string) {
    return this.framesById.get(id);
  }

  closeDisplayFrame(frame: DisplayFrame) {
    const idx = this.frames.indexOf(frame);
    this.frames.splice(idx, 1);
    this.framesById.forEach((other, id) => {
      if (frame === other) {
        this.framesById.delete(id);
      }
    });
    const frameEl = this.scrollPane.children[idx];
    this.scrollPane.removeChild(frameEl);
  }

  bringToFront(frame: DisplayFrame) {
    console.log('bring to front', frame.display.title);
    const idx = this.frames.indexOf(frame);
    if (idx >= 0) {
      this.frames.push(this.frames.splice(idx, 1)[0]);
      const frameEl = this.scrollPane.children[idx];
      this.scrollPane.appendChild(frameEl);
    }
  }
}
