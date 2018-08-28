import { Action } from '../Action';
import Band, { BandOptions } from '../core/Band';
import { EventEvent } from '../events';
import RenderContext from '../RenderContext';
import { G, Line, Rect, Title } from '../tags';
import Timeline from '../Timeline';
import { generateId, isAfter, isBefore, toDate } from '../utils';

export interface SaaBandOptions extends BandOptions {
  events?: any[];
}

export interface SaaBandStyle {
  highlightCursor: string;
  lineColor: string;
  lineWidth: number;
  whiskerColor: string;
  whiskerHeight: number;
  whiskerWidth: number;
}

/**
 * South Atlantic Anomaly (SAA) band.
 * Indicates periods of high radiation on-board ISS
 */
export default class SaaBand extends Band {

  static get type() {
    return 'SaaBand';
  }

  static get rules() {
    return {
      lineColor: '#c13f37',
      lineWidth: 2,
      whiskerColor: '#c13f37',
      whiskerHeight: 5,
      whiskerWidth: 2,
    };
  }

  private eventsById: { [key: string]: object } = {};

  constructor(timeline: Timeline, protected opts: SaaBandOptions, protected style: SaaBandStyle) {
    super(timeline, opts, style);
  }

  renderViewport(ctx: RenderContext) {
    const g = new G();
    const whiskerLead = (this.height - this.style.whiskerHeight) / 2;

    const events = this.opts.events || [];
    for (const event of events) {
      const start = toDate(event.start);
      const stop = toDate(event.stop);

      // Only render if somehow visible within load range
      if (isBefore(start, this.timeline.loadStop) && isAfter(stop, this.timeline.loadStart)) {
        const id = generateId();
        const eventG = new G({ id });
        eventG.addChild(
          new Rect({
            x: ctx.x + this.timeline.positionDate(start),
            y: ctx.y,
            width: this.timeline.pointsBetween(start, stop),
            height: this.height,
            opacity: 0,
          }),
          new Line({
            x1: ctx.x + this.timeline.positionDate(start),
            y1: ctx.y + whiskerLead,
            x2: ctx.x + this.timeline.positionDate(start),
            y2: ctx.y + this.height - whiskerLead,
            stroke: this.style.whiskerColor || this.style.lineColor,
            'stroke-width': this.style.whiskerWidth || this.style.lineWidth,
          }),
          new Line({
            x1: ctx.x + this.timeline.positionDate(stop),
            y1: ctx.y + whiskerLead,
            x2: ctx.x + this.timeline.positionDate(stop),
            y2: ctx.y + this.height - whiskerLead,
            stroke: this.style.whiskerColor || this.style.lineColor,
            'stroke-width': this.style.whiskerWidth || this.style.lineWidth,
          }),
          new Line({
            x1: ctx.x + this.timeline.positionDate(start),
            y1: ctx.y + (this.height / 2),
            x2: ctx.x + this.timeline.positionDate(stop),
            y2: ctx.y + (this.height / 2),
            stroke: this.style.lineColor,
            'stroke-width': this.style.lineWidth,
          }),
        );

        if (event.tooltip) {
          eventG.addChild(new Title({}, event.tooltip));
        }
        if (this.opts.interactive) {
          eventG.setAttribute('cursor', this.style.highlightCursor);
          this.eventsById[id] = event;
          this.timeline.registerActionTarget('click', id);
          this.timeline.registerActionTarget('contextmenu', id);
          this.timeline.registerActionTarget('mouseenter', id);
          this.timeline.registerActionTarget('mousemove', id);
          this.timeline.registerActionTarget('mouseleave', id);
        }
        g.addChild(eventG);
      }
    }

    return g;
  }

  onAction(id: string, action: Action) {
    super.onAction(id, action);
    if (this.eventsById[id]) {
      switch (action.type) {
        case 'click':
          const eventClickEvent = new EventEvent(this.eventsById[id], action);
          this.timeline.fireEvent('eventClick', eventClickEvent);
          break;
        case 'contextmenu':
          const eventContextMenuEvent = new EventEvent(this.eventsById[id], action);
          this.timeline.fireEvent('eventContextMenu', eventContextMenuEvent);
          break;
        case 'mouseenter':
          if (!action.grabbing) {
            const mouseEnterEvent = new EventEvent(this.eventsById[id], action);
            this.timeline.fireEvent('eventMouseEnter', mouseEnterEvent);
          }
          break;
        case 'mousemove':
          const mouseMoveEvent = new EventEvent(this.eventsById[id], action);
          this.timeline.fireEvent('eventMouseMove', mouseMoveEvent);
          break;
        case 'mouseleave':
          const mouseLeaveEvent = new EventEvent(this.eventsById[id], action);
          this.timeline.fireEvent('eventMouseLeave', mouseLeaveEvent);
          break;
      }
    }
  }
}
