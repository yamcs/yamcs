import Plugin, { PluginOptions } from '../Plugin';
import RenderContext from '../RenderContext';
import { Ellipse, G, Line, Rect } from '../tags';
import Timeline from '../Timeline';
import { generateId } from '../utils';

class LocalTimeProvider {

  intervalId: number;

  constructor(locator: any) {
    this.intervalId = window.setInterval(() => {
      locator.updateTime(new Date());
    }, 500);
  }

  stop() {
    clearInterval(this.intervalId);
  }
}

export interface WallclockLocatorOptions extends PluginOptions {

  /**
   * Initial time at which to set the locator. When unset,
   * the locator will be hidden until updateTime is called.
   */
  time?: Date;

  /**
   * When true, data before the locator position will be shaded.
   * Default: false
   */
  markPast?: boolean;

  /**
   * When true, the locator will automatically follow local client time.
   * Default: true
   */
  auto?: boolean;
}

export interface WallclockLocatorStyle {
  pastBackgroundColor: string;
  pastBackgroundOpacity: number;
  knobRadius: number;
  lineColor: string;
  lineWidth: number;
  lineOpacity: number;
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

  time?: Date;
  private clockUpdater: any;

  private wallclockLocatorLineEl: any;
  private wallclockLocatorPastEl: any;

  private wallclockLocatorPastId = generateId();
  private wallclockLocatorLineId = generateId();

  constructor(timeline: Timeline, protected opts: WallclockLocatorOptions, protected style: WallclockLocatorStyle) {
    super(timeline, opts, style);
    this.time = opts.time;
  }

  updateTime(newTime: Date) {
    this.time = newTime;
    if (this.wallclockLocatorLineEl) {
      this.wallclockLocatorLineEl.style.visibility = 'visible';
      let x = this.timeline.pointsBetween(this.timeline.unpannedVisibleStart, newTime);
      x += this.timeline.getSidebarWidth(); // overlay uses the entire space
      this.wallclockLocatorLineEl.childNodes[0].setAttribute('x1', x);
      this.wallclockLocatorLineEl.childNodes[0].setAttribute('x2', x);
      this.wallclockLocatorLineEl.childNodes[1].setAttribute('cx', x);
    }

    if (this.wallclockLocatorPastEl) {
      this.wallclockLocatorPastEl.style.visibility = 'visible';
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
    const xOffset = this.timeline.getSidebarWidth() - ctx.translation.x;

    const visibility = (this.time === undefined) ? 'hidden' : 'visible';

    const time = this.time || new Date();
    const g = new G({ class: 'wallclockLocator' });
    const timeX = xOffset + this.timeline.positionDate(time);

    // Past indicator
    if (this.opts.markPast) {
      const pastG = new G({
        id: this.wallclockLocatorPastId,
        visibility,
      });
      pastG.addChild(new Rect({
        x: xOffset + this.timeline.positionDate(this.timeline.loadStart),
        y: 0,
        width: Math.max(this.timeline.pointsBetween(this.timeline.loadStart, time), 0),
        height: '100%',
        fill: this.style.pastBackgroundColor,
        'fill-opacity': this.style.pastBackgroundOpacity,
        'pointer-events': 'none',
      }));
      g.addChild(pastG);
    }

    g.addChild(new G({
      id: this.wallclockLocatorLineId,
      visibility,
    }).addChild(
      new Line({
        x1: ctx.x + timeX,
        y1: 0,
        x2: ctx.x + timeX,
        y2: '100%',
        stroke: this.style.lineColor,
        'stroke-width': this.style.lineWidth,
        'stroke-opacity': this.style.lineOpacity,
        'stroke-dasharray': '4 3',
        'pointer-events': 'none',
      }),
      new Ellipse({
        cx: ctx.x + timeX,
        cy: 0,
        rx: this.style.knobRadius,
        ry: this.style.knobRadius,
        fill: this.style.lineColor,
        'pointer-events': 'none',
      }),
    ));
    return g;
  }

  postRender(ctx: RenderContext, svgEl: any) {
    this.wallclockLocatorLineEl = svgEl.getElementById(this.wallclockLocatorLineId);
    this.wallclockLocatorPastEl = svgEl.getElementById(this.wallclockLocatorPastId);
    if (this.opts.auto !== false) {
      this.clockUpdater = new LocalTimeProvider(this);
    }
  }

  tearDown() {
    if (this.clockUpdater) {
      this.clockUpdater.stop();
      this.clockUpdater = undefined;
    }
  }
}
