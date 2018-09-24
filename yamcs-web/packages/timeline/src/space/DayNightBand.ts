import { Action } from '../Action';
import Band, { BandOptions } from '../core/Band';
import { EventEvent } from '../events';
import RenderContext from '../RenderContext';
import { G, Line, Rect, Set, Title } from '../tags';
import Timeline from '../Timeline';
import { generateId, isAfter, isBefore, toDate } from '../utils';

export interface DayNightBandOptions extends BandOptions {
  hatchUncovered?: boolean;
  interactiveDays?: boolean;
  events?: any[];
}

export interface DayNightBandStyle {
  hatchFill: string;
  dayColor: string;
  nightColor: string;
  cursor: string;
  dayHoverColor: string;
  nightHoverColor: string;
  dividerColor: string;
}

/**
 * Day/Night terminator band
 * Indicates sun visibility.
 */
export default class DayNightBand extends Band {

  static get type() {
    return 'DayNightBand';
  }

  static get rules() {
    return {
      dayColor: '#fff',
      nightColor: '#000',
      cursor: 'pointer',
      dayHoverColor: '#e0e0e0',
      nightHoverColor: 'grey',
      dividerColor: '#aaa',
      dark: {
        dayColor: '#bbb',
      },
    };
  }

  events: any[];

  private eventsById: { [key: string]: object } = {};

  constructor(timeline: Timeline, public opts: DayNightBandOptions, protected style: DayNightBandStyle) {
    super(timeline, opts, style);
    this.events = opts.events || [];
  }

  renderViewport(ctx: RenderContext) {
    const g = new G();

    if (this.opts.hatchUncovered) {
      g.addChild(new Rect({
        x: ctx.x + this.timeline.positionDate(this.timeline.loadStart),
        y: ctx.y,
        width: this.timeline.pointsBetween(this.timeline.loadStart, this.timeline.loadStop),
        height: this.height,
        fill: this.style.hatchFill,
        'pointer-events': 'none',
      }));
    }

    for (let idx = 0; idx < this.events.length; idx++) {
      const event = this.events[idx];
      const start = toDate(event.start);
      const stop = toDate(event.stop);
      const id = generateId();

      if (isBefore(start, this.timeline.loadStop) && isAfter(stop, this.timeline.loadStart)) {
        const bgRect = new Rect({
          id,
          x: ctx.x + this.timeline.positionDate(start),
          y: ctx.y,
          width: this.timeline.pointsBetween(start, stop),
          height: this.height,
          fill: (event.day ? this.style.dayColor : this.style.nightColor),
        });
        if (event.tooltip) {
          bgRect.addChild(new Title({}, event.tooltip));
        }
        g.addChild(bgRect);

        if (this.opts.interactive) {
          if (!event.day || this.opts.interactiveDays) {
            bgRect.setAttribute('cursor', this.style.cursor);
            bgRect.addChild(new Set({
              attributeName: 'fill',
              to: (event.day ? this.style.dayHoverColor : this.style.nightHoverColor),
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

        // Vertical start divider
        if (!isBefore(start, this.timeline.loadStart)) {
          g.addChild(new Line({
            x1: ctx.x + this.timeline.positionDate(start),
            y1: ctx.y,
            x2: ctx.x + this.timeline.positionDate(start),
            y2: ctx.y + this.height,
            stroke: this.style.dividerColor,
          }));
        }

        // Only draw stop divider if this is the last period
        if (idx === this.events.length - 1 && isBefore(stop, this.timeline.loadStop)) {
          g.addChild(new Line({
            x1: ctx.x + this.timeline.positionDate(stop),
            y1: ctx.y,
            x2: ctx.x + this.timeline.positionDate(stop),
            y2: ctx.y + this.height,
            stroke: this.style.dividerColor,
          }));
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
