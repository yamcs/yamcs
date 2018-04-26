import Band from '../core/Band';
import { EventEvent } from '../events';
import { G, Line, Rect, Title } from '../tags';
import Timeline from '../Timeline';
import RenderContext from '../RenderContext';
import { isAfter, isBefore, toDate } from '../utils';
import { Action } from '../Action';

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

  events: any[];

  private eventsById: { [key: string]: object } = {};

  constructor(timeline: Timeline, opts: any, style: any) {
    super(timeline, opts, style);
    this.events = opts.events || [];
  }

  renderViewport(ctx: RenderContext) {
    const g = new G();
    const whiskerLead = (this.height - this.style['whiskerHeight']) / 2;

    for (const event of this.events) {
      const start = toDate(event.start);
      const stop = toDate(event.stop);

      // Only render if somehow visible within load range
      if (isBefore(start, this.timeline.loadStop) && isAfter(stop, this.timeline.loadStart)) {
        const id = Timeline.nextId();
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
            stroke: this.style['whiskerColor'] || this.style['lineColor'],
            'stroke-width': this.style['whiskerWidth'] || this.style['lineWidth'],
          }),
          new Line({
            x1: ctx.x + this.timeline.positionDate(stop),
            y1: ctx.y + whiskerLead,
            x2: ctx.x + this.timeline.positionDate(stop),
            y2: ctx.y + this.height - whiskerLead,
            stroke: this.style['whiskerColor'] || this.style['lineColor'],
            'stroke-width': this.style['whiskerWidth'] || this.style['lineWidth'],
          }),
          new Line({
            x1: ctx.x + this.timeline.positionDate(start),
            y1: ctx.y + (this.height / 2),
            x2: ctx.x + this.timeline.positionDate(stop),
            y2: ctx.y + (this.height / 2),
            stroke: this.style['lineColor'],
            'stroke-width': this.style['lineWidth'],
          }),
        );

        if (event.tooltip) {
          eventG.addChild(new Title({}, event.tooltip));
        }
        if (this.interactive) {
          eventG.setAttribute('cursor', this.style['highlightCursor']);
          this.eventsById[id] = event;
          this.timeline.registerInteractionTarget(id);
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
        const eventClickEvent = new EventEvent(this.eventsById[id], action.target);
        eventClickEvent.clientX = action.clientX;
        eventClickEvent.clientY = action.clientY;
        this.timeline.fireEvent('eventClick', eventClickEvent);
        break;
      case 'contextmenu':
        const eventContextMenuEvent = new EventEvent(this.eventsById[id], action.target);
        eventContextMenuEvent.clientX = action.clientX;
        eventContextMenuEvent.clientY = action.clientY;
        this.timeline.fireEvent('eventContextMenu', eventContextMenuEvent);
        break;
      case 'mouseenter':
        const mouseEnterEvent = new EventEvent(this.eventsById[id], action.target);
        mouseEnterEvent.clientX = action.clientX;
        mouseEnterEvent.clientY = action.clientY;
        this.timeline.fireEvent('eventMouseEnter', mouseEnterEvent);
        break;
      case 'mousemove':
        const mouseMoveEvent = new EventEvent(this.eventsById[id], action.target);
        mouseMoveEvent.clientX = action.clientX;
        mouseMoveEvent.clientY = action.clientY;
        this.timeline.fireEvent('eventMouseMove', mouseMoveEvent);
        break;
      case 'mouseleave':
        const mouseLeaveEvent = new EventEvent(this.eventsById[id], action.target);
        mouseLeaveEvent.clientX = action.clientX;
        mouseLeaveEvent.clientY = action.clientY;
        this.timeline.fireEvent('eventMouseLeave', mouseLeaveEvent);
        break;
      }
    }
  }
}
