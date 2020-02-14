import { Action } from '../Action';
import Band, { BandOptions } from '../core/Band';
import { EventEvent } from '../events';
import RenderContext from '../RenderContext';
import { Ellipse, G, Line, Rect, Set, Text, Title } from '../tags';
import Timeline from '../Timeline';
import { generateId, isAfter, isBefore, toDate } from '../utils';

const leftTextMargin = 20;

export interface AttitudeBandOptions extends BandOptions {
  hatchUncovered?: boolean;
  events?: any[];
}

export interface AttitudeBandStyle {
  lineHeight: number;
  textSize: number;
  textColor: string;
  connectorColor: string;
  dotColor: string;
  dotRadius: number;
  hatchFill: string;
  bandBackgroundColor: string;
  highlightCursor: string;
  eventHoverBackground: string;
}

/**
 * Attitude of a vehicle. Center dots indicate a change
 */
export default class AttitudeBand extends Band {

  static get type() {
    return 'AttitudeBand';
  }

  static get rules() {
    return {
      dotColor: '#000',
      dotRadius: 2,
      connectorColor: 'grey',
      textColor: 'grey',
      highlightOpacity: 0.2,
      eventHoverBackground: '#e2e2e2',
      dark: {
        dotColor: '#fff',
      },
    };
  }

  events: any[];
  hatchUncovered: boolean;
  lines: any[];
  lineHeight: any;
  bandHeight: number;

  private eventsById: { [key: string]: object } = {};

  constructor(timeline: Timeline, protected opts: AttitudeBandOptions, protected style: AttitudeBandStyle) {
    super(timeline, opts, style);

    this.events = opts.events || [];
    this.hatchUncovered = (opts.hatchUncovered !== false);
    this.lines = this.spreadAcrossLines(this.events);
    this.lineHeight = style.lineHeight;
    this.bandHeight = Math.max(this.lineHeight, this.lineHeight * this.lines.length);
  }

  /**
   * Attitude changes are not overlapping by themselves.
   * However, the labels could still overlap.
   */
  spreadAcrossLines(events: any[]) {
    const lines = [];
    for (const event of events) {
      const start = toDate(event.start);
      const stop = toDate(event.stop);
      const fm = this.timeline.getFontMetrics(event.attitude, this.style.textSize);

      // Only consider if somehow visible within load range
      if (isBefore(start, this.timeline.loadStop) && isAfter(stop, this.timeline.loadStart)) {
        let inserted = false;
        const start_x = this.timeline.positionDate(start);
        const stop_x = start_x + leftTextMargin + fm['width'];
        for (const line of lines) {
          let min = 0;
          let max = line.length - 1;
          while (min <= max) {
            const mid = Math.floor((min + max) / 2);
            const midFm = this.timeline.getFontMetrics(line[mid].attitude, this.style.textSize);
            const mid_start_x = this.timeline.positionDate(toDate(line[mid].start));
            const mid_stop_x = mid_start_x + leftTextMargin + midFm['width'];
            if (stop_x <= mid_start_x) {
              max = mid - 1; // Put cursor before mid
            } else if (start_x >= mid_stop_x) {
              min = mid + 1; // Put cursor after mid
            } else {
              break; // Overlap
            }
          }
          if (min > max) {
            line.splice(min, 0, event);
            inserted = true;
            break;
          }
        }

        if (!inserted) {
          lines.push([event]); // A new line
        }
      }
    }
    return lines;
  }

  overlaps(needle: any) {
    const needleStart = this.timeline.positionDate(toDate(needle.start));
    const fm = this.timeline.getFontMetrics(needle.attitude, this.style.textSize);
    const needleStop = needleStart + leftTextMargin + fm['width'];
    for (const hay of this.events) {
      if (needle !== hay) {
        const otherStart = this.timeline.positionDate(toDate(hay.start));
        const otherFm = this.timeline.getFontMetrics(hay.attitude, this.style.textSize);
        const otherStop = otherStart + leftTextMargin + otherFm['width'];
        if (needleStart < otherStop && needleStop > otherStart) {
          return true;
        }
      }
    }
    return false;
  }

  get height() {
    return this.bandHeight;
  }

  renderViewport(ctx: RenderContext) {
    const g = new G();

    if (this.hatchUncovered) {
      g.addChild(new Rect({
        x: ctx.x + this.timeline.positionDate(this.timeline.loadStart),
        y: ctx.y,
        width: this.timeline.pointsBetween(this.timeline.loadStart, this.timeline.loadStop),
        height: this.height,
        fill: this.style.hatchFill,
        'pointer-events': 'none',
      }));
    }

    for (const line of this.lines) {
      for (const event of line) {
        const start = toDate(event.start);
        const stop = toDate(event.stop);
        const id = generateId();

        const bgRect = new Rect({
          id,
          x: ctx.x + this.timeline.positionDate(start),
          y: ctx.y,
          width: this.timeline.pointsBetween(start, stop),
          height: this.height,
          fill: this.style.bandBackgroundColor,
        });
        if (event.tooltip) {
          bgRect.addChild(new Title({}, event.tooltip));
        }
        g.addChild(bgRect);

        if (this.opts.interactive) {
          bgRect.setAttribute('cursor', this.style.highlightCursor);
          bgRect.addChild(new Set({
            attributeName: 'fill',
            to: this.style.eventHoverBackground,
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

    // Second pass for adding content (this ensures they are on top of background fill)
    for (let idx = 0; idx < this.lines.length; idx++) {
      const line = this.lines[idx];
      const offsetY = ctx.y + (idx * this.lineHeight);
      for (const event of line) {
        const start = toDate(event.start);
        let startX = ctx.x + this.timeline.positionDate(start) + leftTextMargin;
        const stop = toDate(event.stop);
        const fm = this.timeline.getFontMetrics(event.attitude, this.style.textSize);

        let textY = ctx.y + (this.bandHeight / 2);
        if (this.overlaps(event)) {
          textY = offsetY + (this.lineHeight / 2);
        }

        // Connector between dot and text
        g.addChild(new Line({
          x1: ctx.x + this.timeline.positionDate(start),
          y1: ctx.y + (this.height / 2),
          x2: startX - 3, // The 3 is some small breather room
          y2: textY,
          stroke: this.style.connectorColor,
        }));

        // Add text directly after start divider
        g.addChild(new Text({
          x: startX,
          y: textY,
          fill: this.style.textColor,
          'text-anchor': 'left',
          'dominant-baseline': 'middle',
          'font-size': this.style.textSize,
          'pointer-events': 'none',
        }, event.attitude));

        // Start dot
        if (!isBefore(start, this.timeline.loadStart)) {
          g.addChild(new Ellipse({
            cx: ctx.x + this.timeline.positionDate(start),
            cy: ctx.y + (this.bandHeight / 2),
            rx: this.style.dotRadius,
            ry: this.style.dotRadius,
            fill: this.style.dotColor,
          }));
        }

        const distance = this.timeline.pointsBetween(start, stop);

        // Repeat while we fit in the load range.
        // Intentionally not using an svg pattern because we don't want anything clipped.
        if (distance > 2 * fm['width']) {
          const maxTextStart = startX + this.timeline.pointsBetween(start, stop) - (2 * fm['width']);
          startX += 2 * fm['width'];
          while (startX <= maxTextStart) {
            g.addChild(new Text({
              x: startX,
              y: ctx.y + (this.height / 2),
              fill: this.style.textColor,
              'text-anchor': 'left',
              'dominant-baseline': 'middle',
              'font-size': this.style.textSize,
              'pointer-events': 'none',
            }, event.attitude));
            startX += 2 * fm['width'];
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
