import Plugin from '../Plugin';
import { Range } from '../Range';
import RenderContext from '../RenderContext';
import { G, Line, Rect } from '../tags';
import { generateId } from '../utils';

/**
 * Highlights horizontal range selections
 */
export default class HorizontalSelection extends Plugin {

  static get type() {
    return 'HorizontalSelection';
  }

  static get rules() {
    return {
      backgroundColor: 'lightgrey',
      backgroundOpacity: 0.2,
      lineColor: 'black',
      lineOpacity: 0.4,
      dark: {
        backgroundColor: 'grey',
      },
    };
  }

  private trackerId = generateId();
  private rectId = generateId();
  private lineLeftId = generateId();
  private lineRightId = generateId();

  private ctx: RenderContext;
  private svgEl: any;

  renderViewportXOverlay(ctx: RenderContext) {
    return new G({
      id: this.trackerId,
      style: 'visibility: hidden',
    }).addChild(new Rect({
      id: this.rectId,
      x: ctx.x,
      y: 0,
      width: 0,
      height: '100%',
      fill: this.style.backgroundColor,
      'fill-opacity': this.style.backgroundOpacity,
      'pointer-events': 'none',
    }), new Line({
      id: this.lineLeftId,
      x1: ctx.x,
      y1: 0,
      x2: 0,
      y2: '100%',
      stroke: this.style.lineColor,
      'stroke-dasharray': '4 3',
      'stroke-opacity': this.style.lineOpacity,
      'pointer-events': 'none'
    }), new Line({
      id: this.lineRightId,
      x1: ctx.x,
      y1: 0,
      x2: 0,
      y2: '100%',
      stroke: this.style.lineColor,
      'stroke-opacity': this.style.lineOpacity,
      'stroke-dasharray': '4 3',
      'pointer-events': 'none'
    }));
  }

  setSelection(range: Range) {
    const trackerEl = this.svgEl.getElementById(this.trackerId);
    const rectEl = this.svgEl.getElementById(this.rectId);
    const lineLeftEl = this.svgEl.getElementById(this.lineLeftId);
    const lineRightEl = this.svgEl.getElementById(this.lineRightId);

    // Incoming ctx is not very useful as an origin
    // TODO try to fix the need for this xOffset
    const xOffset = this.timeline.getSidebarWidth() - this.ctx.translation.x;
    const x1 = xOffset + this.timeline.positionDate(range.start);
    const x2 = xOffset + this.timeline.positionDate(range.stop);

    rectEl.setAttribute('x', x1);
    rectEl.setAttribute('width', x2 - x1);

    lineLeftEl.setAttribute('x1', x1);
    lineLeftEl.setAttribute('x2', x1);

    lineRightEl.setAttribute('x1', x2);
    lineRightEl.setAttribute('x2', x2);

    trackerEl.style.visibility = 'visible';
  }

  clearSelection() {
    const trackerEl = this.svgEl.getElementById(this.trackerId);
    if (trackerEl) {
      trackerEl.style.visibility = 'hidden';
    }
  }

  postRender(ctx: RenderContext, svgEl: any) {
    this.ctx = ctx;
    this.svgEl = svgEl;

    // Re-apply active range if this is a re-rendering
    const selectedRange = this.timeline.selectedRange;
    if (selectedRange) {
      this.setSelection(selectedRange);
    }
  }
}
