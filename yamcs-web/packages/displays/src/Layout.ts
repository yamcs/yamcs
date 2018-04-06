import { DisplayFrame, Coordinates } from './DisplayFrame';
import { LayoutState, FrameState } from './LayoutState';
import { DisplayCommunicator } from './DisplayCommunicator';

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
    readonly displayCommunicator: DisplayCommunicator,
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
    // TODO? window.clearInterval(this.synchronizer);
  }

  createDisplayFrame(id: string, coordinates: Coordinates = { x: 20, y: 20 }) {
    if (this.framesById.has(id)) {
      throw new Error(`Layout already contains a frame with id ${id}`);
    }
    const frame = new DisplayFrame(id, this.scrollPane, this, coordinates);
    this.frames.push(frame);
    this.framesById.set(id, frame);
    return frame.loadAsync().then(() => {
      this.layoutListeners.forEach(l => l.onDisplayFrameOpen(frame));
      this.fireStateChange();
    });
  }

  /**
   * Repositions frames so they stack from top-left to bottom-right
   * in their current visibility order.
   */
  cascadeFrames() {
    let pos = 20;
    for (const frame of this.frames) {
      frame.setPosition(pos, pos);
      pos += 20;
    }
    this.fireStateChange();
  }

  /**
   * Fits all frames in a grid that fits in the available width/height.
   * Frames are scaled as needed.
   */
  tileFrames() {
    // Determine grid size
    const sqrt = Math.floor(Math.sqrt(this.frames.length));
    let rows = sqrt;
    let cols = sqrt;
    if (rows * cols < this.frames.length) {
      cols++;
      if (rows * cols < this.frames.length) {
        rows++;
      }
    }

    const gutter = 20;
    // clientWidth excludes size of scrollbars
    const w = (this.scrollPane.clientWidth - gutter - (cols * gutter)) / cols;
    const h = (this.scrollPane.clientHeight - gutter - (rows * gutter)) / rows;
    let x = gutter;
    let y = gutter;
    for (let i = 0; i < rows; i++) {
      for (let j = 0; j < cols && ((i * cols) + j < this.frames.length); j++) {
        const frame = this.frames[(i * cols) + j];
        frame.setPosition(x, y);
        frame.setDimension(w, h - frame.titleBarHeight);
        x += w + gutter;
      }
      y += h + gutter;
      x = gutter;
    }
    this.fireStateChange();
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

  clear() {
    const framesCopy = [...this.frames];
    for (const frame of framesCopy) {
      this.closeDisplayFrame(frame);
    }
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
