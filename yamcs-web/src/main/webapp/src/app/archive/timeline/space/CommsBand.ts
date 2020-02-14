import { Action } from '../Action';
import Band, { BandOptions } from '../core/Band';
import { EventEvent } from '../events';
import RenderContext from '../RenderContext';
import { G, Rect, Set, Title } from '../tags';
import Timeline from '../Timeline';
import { generateId, isAfter, isBefore, toDate } from '../utils';

export interface CommsBandOptions extends BandOptions {
  bands?: any[];
  events?: any[];
}

export interface CommsBandStyle {
  topMargin: number;
  bottomMargin: number;
  highlightCursor: string;
  highlightOpacity: number;
}

/**
 * Band indicating availability of one or more communication bands.
 */
export default class CommsBand extends Band {

  static get type() {
    return 'CommsBand';
  }

  static get rules() {
    return {
      topMargin: 0,
      bottomMargin: 0,
      highlightOpacity: 0.7,
    };
  }

  private commsBands: any[];
  private events: any[];

  private eventsById: { [key: string]: object } = {};

  constructor(timeline: Timeline, protected opts: CommsBandOptions, protected style: CommsBandStyle) {
    super(timeline, opts, style);

    this.commsBands = opts.bands || [];
    this.events = opts.events || [];
  }

  renderViewport(ctx: RenderContext) {
    const g = new G();
    const barHeight = (this.height - this.style.topMargin - this.style.bottomMargin) / this.commsBands.length;

    for (const event of this.events) {
      const start = toDate(event.start);
      const stop = toDate(event.stop);

      // Only render if somehow visible within load range
      if (isBefore(start, this.timeline.loadStop) && isAfter(stop, this.timeline.loadStart)) {
        for (let i = 0; i < this.commsBands.length; i++) {
          const commsBand = this.commsBands[i];
          if (event[commsBand['type']]) {
            const id = generateId();
            const bgRect = new Rect({
              id,
              x: ctx.x + this.timeline.positionDate(start),
              y: ctx.y + this.style.topMargin + (i * barHeight),
              width: this.timeline.pointsBetween(start, stop),
              height: barHeight,
              fill: commsBand['color'],
            });
            if (event.tooltip) {
              bgRect.addChild(new Title({}, event.tooltip));
            }
            g.addChild(bgRect);

            if (this.opts.interactive) {
              bgRect.setAttribute('cursor', this.style.highlightCursor);
              bgRect.addChild(new Set({
                attributeName: 'opacity',
                to: this.style.highlightOpacity,
                begin: 'mouseover',
                end: 'mouseout',
              }));
              this.eventsById[id] = event;
              this.timeline.registerActionTarget('click', id);
              this.timeline.registerActionTarget('contextmenu', id);
              this.timeline.registerActionTarget('mouseenter', id);
              this.timeline.registerActionTarget('mousemove', id);
              this.timeline.registerActionTarget('mouseleave', id);
            }
          }
        }
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
