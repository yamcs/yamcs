import { ResourceResolver } from './ResourceResolver';
import { DisplayFrame, Coordinates } from './DisplayFrame';
import { StyleSet } from './StyleSet';
import { LayoutState, FrameState } from './LayoutState';

export interface LayoutListener {

  onDisplayFrameOpen(frame: DisplayFrame): void;

  onDisplayFrameClose(frame: DisplayFrame): void;
}

export interface LayoutStateListener {

  onStateChange(state: LayoutState): void;
}

export class Layout {

  scrollPane: HTMLDivElement;

  // Sorted by depth (back -> front)
  frames: DisplayFrame[] = [];

  framesById = new Map<string, DisplayFrame>();

  synchronizer: number;

  layoutListeners = new Set<LayoutListener>();
  layoutStateListeners = new Set<LayoutStateListener>();

  /**
   * Limit client-side update to this amount of milliseconds.
   */
  private updateRate = 500;

  constructor(
    private targetEl: HTMLDivElement,
    readonly styleSet: StyleSet,
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

  createDisplayFrame(id: string, doc: XMLDocument, coordinates: Coordinates = { x: 20, y: 20 }) {
    if (this.framesById.has(id)) {
      throw new Error(`Layout already contains a frame with id ${id}`);
    }
    const frame = new DisplayFrame(id, this.scrollPane, this, doc, coordinates);
    this.frames.push(frame);
    this.framesById.set(id, frame);
    this.layoutListeners.forEach(l => l.onDisplayFrameOpen(frame));
    this.fireStateChange();
    return frame;
  }

  hasDisplayFrame(id: string) {
    return this.framesById.has(id);
  }

  getDisplayFrame(id: string) {
    return this.framesById.get(id);
  }

  fireStateChange() {
    const state = this.getLayoutState();
    this.layoutStateListeners.forEach(l => l.onStateChange(state));
  }

  /**
   * Returns a JSON structure describing the current layout contents
   */
  getLayoutState(): LayoutState {
    const frameStates: FrameState[] = [];
    for (const frame of this.frames) { // Keep front-to-back order
      frameStates.push({ id: frame.id, ...frame.getCoordinates() });
    }
    return { frames: frameStates };
  }

  closeDisplayFrame(frame: DisplayFrame) {
    this.layoutListeners.forEach(l => l.onDisplayFrameClose(frame));
    const idx = this.frames.indexOf(frame);
    this.frames.splice(idx, 1);
    this.framesById.forEach((other, id) => {
      if (frame === other) {
        this.framesById.delete(id);
      }
    });
    const frameEl = this.scrollPane.children[idx];
    this.scrollPane.removeChild(frameEl);
    this.fireStateChange();
  }

  bringToFront(frame: DisplayFrame) {
    const idx = this.frames.indexOf(frame);
    if (0 <= idx && idx < this.frames.length - 1) {
      this.frames.push(this.frames.splice(idx, 1)[0]);
      const frameEl = this.scrollPane.children[idx];
      this.scrollPane.appendChild(frameEl);
      this.fireStateChange();
    }
  }
}
