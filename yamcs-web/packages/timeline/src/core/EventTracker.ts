import { G, Line, Rect } from '../tags';
import { toDate } from '../utils';
import { EventEvent } from '../events';
import Timeline from '../Timeline';
import RenderContext from '../RenderContext';
import Plugin from '../Plugin';

/**
 * Highlights active events, based on mouse position
 */
export default class EventTracker extends Plugin {

  static get type() {
    return 'EventTracker';
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

  private trackerId: string = Timeline.nextId();
  private rectId: string = Timeline.nextId();
  private lineLeftId: string = Timeline.nextId();
  private lineRightId: string = Timeline.nextId();

  private eventMouseEnterListener: (event: EventEvent) => any;
  private eventMouseLeaveListener: (event: EventEvent) => any;

  renderViewportXOverlay(ctx: RenderContext) {
    return new G({
      id: this.trackerId,
      style: 'visibility: hidden',
    }).addChild(new Rect({
      id: this.rectId,
      x: ctx.x,
      y: 0,
      width: 0,
      height: ctx.totalHeight,
      fill: this.style.backgroundColor,
      'fill-opacity': this.style.backgroundOpacity,
      'pointer-events': 'none',
    }), new Line({
      id: this.lineLeftId,
      x1: ctx.x,
      y1: 0,
      x2: 0,
      y2: ctx.totalHeight,
      stroke: this.style.lineColor,
      'stroke-dasharray': '4 3',
      'stroke-opacity': this.style.lineOpacity,
    }), new Line({
      id: this.lineRightId,
      x1: ctx.x,
      y1: 0,
      x2: 0,
      y2: ctx.totalHeight,
      stroke: this.style.lineColor,
      'stroke-opacity': this.style.lineOpacity,
      'stroke-dasharray': '4 3',
    }));
  }

  postRender(ctx: RenderContext, svgEl: any) {
    const trackerEl = svgEl.getElementById(this.trackerId);
    const rectEl = svgEl.getElementById(this.rectId);
    const lineLeftEl = svgEl.getElementById(this.lineLeftId);
    const lineRightEl = svgEl.getElementById(this.lineRightId);
    const timeline = this.timeline;

    this.eventMouseEnterListener = function(obj: any) {
      if (obj && obj.userObject && obj.userObject['start'] && obj.userObject['stop']) {

        // Incoming coordinates are within body viewport, but since this is an overlay,
        // we need to convert that
        const x1 = timeline.positionDate(toDate(obj.userObject['start'])) + ctx.sidebarWidth;
        const x2 = timeline.positionDate(toDate(obj.userObject['stop'])) + ctx.sidebarWidth;

        rectEl.setAttribute('x', x1);
        rectEl.setAttribute('width', x2 - x1);

        lineLeftEl.setAttribute('x1', x1);
        lineLeftEl.setAttribute('x2', x1);

        lineRightEl.setAttribute('x1', x2);
        lineRightEl.setAttribute('x2', x2);

        trackerEl.style.visibility = 'visible';
      }
    };

    this.eventMouseLeaveListener = function() {
      if (rectEl) {
        trackerEl.style.visibility = 'hidden';
      }
    };

    this.timeline.on('eventMouseEnter', this.eventMouseEnterListener);
    this.timeline.on('eventMouseLeave', this.eventMouseLeaveListener);
  }

  tearDown() {
    this.timeline.off('eventMouseEnter', this.eventMouseEnterListener);
    this.timeline.off('eventMouseLeave', this.eventMouseLeaveListener);
  }
}
