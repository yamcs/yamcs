import { Ellipse, G, Line, Rect } from '../tags';
import Timeline from '../Timeline';
import RenderContext from '../RenderContext';
import Plugin from '../Plugin';

class LocalTimeProvider {

  _intervalId: number;

  constructor(ctx: RenderContext, locator: any) {
    this._intervalId = window.setInterval(() => {
      locator.updateTime(ctx, new Date());
    }, 500);
  }

  stop() {
    clearInterval(this._intervalId);
  }
}

/**
 * Vertical Time Locator
 */
export default class WallclockLocator extends Plugin {

  static get type(): string {
    return 'WallclockLocator';
  }

  static get rules() {
    return {
      knobColor: 'red',
      knobRadius: 3,
      lineColor: 'red',
      lineWidth: '1px',
      lineOpacity: 0.6,
      pastBackgroundColor: 'lightgrey',
      pastBackgroundOpacity: 0.4,
    };
  }

  time: Date;
  clockUpdater: any;

  wallclockLocatorLineEl: any;
  wallclockLocatorPastEl: any;

  constructor(timeline: Timeline, opts: any, style: any) {
    super(timeline, opts, style);
    this.time = opts.time || new Date();
  }

  updateTime(ctx: RenderContext, newTime: Date) {
    this.time = newTime;
    if (this.wallclockLocatorLineEl) {
      let x = this.timeline.pointsBetween(this.timeline.unpannedVisibleStart, newTime);
      x += ctx.sidebarWidth; // overlay uses the entire space
      this.wallclockLocatorLineEl.childNodes[0].setAttribute('x1', x);
      this.wallclockLocatorLineEl.childNodes[0].setAttribute('x2', x);
      this.wallclockLocatorLineEl.childNodes[1].setAttribute('cx', x);
      this.wallclockLocatorLineEl.childNodes[2].setAttribute('cx', x);
    }

    if (this.wallclockLocatorPastEl) {
      // For past-overlay, just adjust width
      const pointsBetween = this.timeline.pointsBetween(this.timeline.loadStart, newTime);
      const x = String(Math.max(pointsBetween, 0));
      for (const node of this.wallclockLocatorPastEl.childNodes) {
        node.setAttribute('width', x);
      }
    }
  }

  renderViewportXOverlay(ctx: RenderContext) {
    // Incoming ctx is not very useful as an origin
    // TODO try to fix the need for this xOffset
    const xOffset = ctx.sidebarWidth - ctx.translation.x;

    const time = this.time;
    const g = new G({ class: 'wallclockLocator' });
    const timeX = xOffset + this.timeline.positionDate(time);

    // Past indicator
    if (this.opts.markPast) {
      const pastG = new G({ class: 'wallclockLocatorPast' });
      pastG.addChild(new Rect({
        x: xOffset + this.timeline.positionDate(this.timeline.loadStart),
        y: 0,
        width: Math.max(this.timeline.pointsBetween(this.timeline.loadStart, time), 0),
        height: ctx.totalHeight,
        fill: this.style['pastBackgroundColor'],
        'fill-opacity': this.style['pastBackgroundOpacity'],
        'pointer-events': 'none',
      }));
      g.addChild(pastG);
    }

    g.addChild(new G({ class: 'wallclockLocatorLine' }).addChild(
      new Line({
        x1: ctx.x + timeX,
        y1: this.style['knobRadius'],
        x2: ctx.x + timeX,
        y2: ctx.totalHeight - this.style['knobRadius'],
        stroke: this.style['lineColor'],
        'stroke-width': this.style['lineWidth'],
        'stroke-opacity': this.style['lineOpacity'],
        'stroke-dasharray': '4 3',
        'pointer-events': 'none',
      }),
      new Ellipse({
        cx: ctx.x + timeX,
        cy: 0,
        rx: this.style['knobRadius'],
        ry: this.style['knobRadius'],
        fill: this.style['lineColor'],
      }),
      new Ellipse({
        cx: ctx.x + timeX,
        cy: ctx.totalHeight,
        rx: this.style['knobRadius'],
        ry: this.style['knobRadius'],
        fill: this.style['lineColor'],
      }),
    ));
    return g;
  }

  postRender(ctx: RenderContext, svgEl: any) {
    this.wallclockLocatorLineEl = svgEl.getElementsByClassName('wallclockLocatorLine')[0];
    this.wallclockLocatorPastEl = svgEl.getElementsByClassName('wallclockLocatorPast')[0];
    this.clockUpdater = new LocalTimeProvider(ctx, this);
  }

  tearDown() {
    if (this.clockUpdater) {
      this.clockUpdater.stop();
      this.clockUpdater = undefined;
    }
  }
}
