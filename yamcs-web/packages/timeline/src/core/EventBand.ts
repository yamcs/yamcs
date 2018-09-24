import { Action } from '../Action';
import { EventChangedEvent, EventEvent } from '../events';
import Point from '../Point';
import RenderContext from '../RenderContext';
import { ClipPath, Ellipse, G, Line, Path, Rect, Set, Text, Title } from '../tags';
import Timeline from '../Timeline';
import { generateId, isAfter, isBefore, toDate } from '../utils';
import Band, { BandOptions } from './Band';

/**
 * Wraps an event with internal properties. These are intentionally not
 * set on the user object.
 */
interface EventWithDrawInfo {
  userObject: Event; // Untouched user object
  drawInfo?: DrawInfo;
}

interface DrawInfo {
  id: string;
  renderStartX: number;
  renderStopX: number;
  textOutside: boolean;
  milestone: boolean;
  availableTitleWidth: number;
  offscreenStart: boolean;
}

export interface Event {
  start: string | Date;
  stop?: string | Date;
  milestone?: boolean;
  title?: string;
  backgroundColor?: string;
  foregroundColor?: string;
  borderColor?: string;
  borders?: boolean | 'vertical';
  tooltip?: string;
  textAlign?: string;
  data?: any;
}

export interface EventBandOptions extends BandOptions {

  /**
   * Events that will be drawn in the band.
   */
  events?: Event[];

  /**
   * Whether ranges without a single overlapping event should be rendered
   * with a cross-hatch pattern. Default: false.
   */
  hatchUncovered?: boolean;

  /**
   * Whether the event's background color should be extended vertically (with opacity).
   * Default: false.
   */
  leakEventBackground?: boolean;

  /**
   * If true, events on this band may be dragged from one location
   * to another. Default: false
   */
  draggable?: boolean;

  /**
   * If true, events on this band may be resized. Default: true
   */
  resizable?: boolean;

  /**
   * If true, events are layed out on a single line even when there's overlap. Default: false
   */
  wrap?: boolean;
}

export interface EventBandStyle {
  backgroundColor: string;
  borderColor: string;
  textColor: string;
  textSize: number;
  textAlign: string;
  cornerRadius: number;
  eventLeftMargin: number;
  eventHeight: number;
  lineHeight: number;
  spaceBetween: number;
  lineSpacing: number;
  marginTop: number;
  marginBottom: number;
  hatchFill: string;
  borders: boolean | 'vertical';
  highlightOpacity: number;
  highlightCursor: string;
}

/**
 * Basic band for showing events in boxes.
 *
 * Within the band, events are allowed to overlap. An algorithm
 * takes care of spreading it over multiple lines in define-order.
 */
export default class EventBand extends Band {

  static get type() {
    return 'EventBand';
  }

  static get rules() {
    return {
      backgroundColor: '#529bff',
      borderColor: '#0a56bc',
      textColor: '#1c4b8b',
      textAlign: 'left',
      cornerRadius: 1,
      eventLeftMargin: 5,
      highlightOpacity: 0.7,
      spaceBetween: 0,
      lineSpacing: 2,
      marginTop: 0,
      marginBottom: 0,
      // eventHeight: 15, // defaults to lineHeight
      borders: false, // false, true, 'vertical'
      dark: {
        // backgroundColor: '#4c4c4c',
        // borderColor: '',
        // textColor: '#bbb',
      },
    };
  }

  private lines: EventWithDrawInfo[][];
  private eventHeight: number;
  private bandHeight: number;

  private grabStart?: Point;

  private eventsById: { [key: string]: EventWithDrawInfo } = {};

  constructor(timeline: Timeline, protected opts: EventBandOptions, protected style: EventBandStyle) {
    super(timeline, opts, style);
    this.eventHeight = style.eventHeight || style.lineHeight!;
    this.prepareDrawOperation(opts.events || []);
  }

  private prepareDrawOperation(events: Event[]) {
    const analyzedEvents = this.analyzeEvents(events);

    // Milestones are on top per band
    this.lines = [
      ...this.spreadAcrossLines(analyzedEvents.filter(evt => (evt.drawInfo && evt.drawInfo.milestone))),
      ...this.spreadAcrossLines(analyzedEvents.filter(evt => (evt.drawInfo && !evt.drawInfo.milestone))),
    ];

    this.bandHeight = this.style.marginTop + (this.eventHeight * this.lines.length) + (this.style.lineSpacing * (this.lines.length - 1)) + this.style.marginBottom;

    // Band could be empty
    this.bandHeight = Math.max(this.bandHeight, this.style.marginTop + this.eventHeight + this.style.marginBottom);

    // console.log('height is ', this.bandHeight)
  }

  private analyzeEvents(events: Event[]) {
    const analyzedEvents: EventWithDrawInfo[] = [];
    for (const event of events) {
      const start = toDate(event.start);
      let stop;
      let milestone = event.milestone;
      if (milestone === undefined) {
        milestone = !event.stop || (toDate(event.stop).getTime() === start.getTime());
      }
      let renderStartX: number;
      let renderStopX: number;
      let textOutside: boolean;
      let availableTitleWidth: number;
      let offscreenStart: boolean;

      // Events without duration are considered 'milestones'
      // Events with duration are only considered 'milestones' when the property is set
      if (milestone) {
        stop = (event.stop) ? toDate(event.stop) : start;
      } else {
        stop = toDate(event.stop);
      }

      // Only consider if somehow visible within load range
      let analyzedEvent: EventWithDrawInfo;
      if (isBefore(start, this.timeline.loadStop) && isAfter(stop, this.timeline.loadStart)) {

        // Overlap is determined using render width (which may include a label)
        if (milestone) {
          renderStartX = this.timeline.positionDate(start) - (this.eventHeight / 2);
          renderStopX = renderStartX + this.eventHeight;

          if (event.title) {
            const fm = this.timeline.getFontMetrics(event.title, this.style.textSize!);
            renderStopX += fm.width + this.style.eventLeftMargin;
          }
          textOutside = true;
          availableTitleWidth = 0;
          offscreenStart = false;
        } else {
          renderStartX = this.timeline.positionDate(start);
          renderStopX = this.timeline.positionDate(stop);
          offscreenStart = isBefore(start, this.timeline.visibleStart) && isAfter(stop, this.timeline.visibleStart);

          let title = event.title;
          if (title && offscreenStart) {
            title = '◀' + title;
          }

          const fm = this.timeline.getFontMetrics(title!, this.style.textSize);

          availableTitleWidth = renderStopX - renderStartX;
          if (offscreenStart) {
            availableTitleWidth = renderStopX - this.timeline.positionDate(this.timeline.visibleStart);
          }

          textOutside = false;
          if (this.opts.wrap !== false && availableTitleWidth < fm.width) {
            renderStopX += fm.width + this.style.eventLeftMargin;
            textOutside = true;
          }
        }

        const id = generateId();
        if (this.opts.interactive) {
          this.timeline.registerActionTarget('click', id);
          this.timeline.registerActionTarget('contextmenu', id);
          this.timeline.registerActionTarget('mouseenter', id);
          this.timeline.registerActionTarget('mousemove', id);
          this.timeline.registerActionTarget('mouseleave', id);

          if (this.opts.draggable) {
            this.timeline.registerActionTarget('grabstart', id);
            this.timeline.registerActionTarget('grabmove', id);
            this.timeline.registerActionTarget('grabend', id);
          }
        }

        analyzedEvent = {
          userObject: event,
          drawInfo: {
            id,
            renderStartX,
            renderStopX,
            milestone,
            textOutside,
            availableTitleWidth,
            offscreenStart,
          }
        };
        this.eventsById[id] = analyzedEvent;
      } else {
        analyzedEvent = {
          userObject: event,
        };
      }

      analyzedEvents.push(analyzedEvent);
    }
    return analyzedEvents;
  }

  /**
   * Splits the provided events in different lines so that nothing overlaps within a line.
   * The calculation converts dates to points, but this is mainly to have the option
   * of defining a minimum space in points between two line events.
   */
  private spreadAcrossLines(events: EventWithDrawInfo[]) {
    const lines: EventWithDrawInfo[][] = [];
    for (const event of events) {

      let inserted = false;
      const startX = event.drawInfo!.renderStartX;
      const stopX = event.drawInfo!.renderStopX;
      for (const line of lines) {
        let min = 0;
        let max = line.length - 1;
        while (min <= max) {
          const mid = Math.floor((min + max) / 2);
          const midStartX = line[mid].drawInfo!.renderStartX;
          const midStopX = line[mid].drawInfo!.renderStopX;
          if ((stopX + this.style.spaceBetween) <= midStartX) {
            max = mid - 1; // Put cursor before mid
          } else if (startX >= (midStopX + this.style.spaceBetween)) {
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

    return lines;
  }

  get height() {
    return this.bandHeight;
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

    for (let idx = 0; idx < this.lines.length; idx++) {
      const line = this.lines[idx];
      let offsetY = ctx.y + this.style.marginTop;
      offsetY += idx * (this.style.lineSpacing + this.eventHeight);

      for (const event of line) {
        if (event.drawInfo!.milestone) {
          const milestoneG = this.renderMilestone(event, ctx.x, offsetY);
          g.addChild(milestoneG);
        } else {
          const eventG = this.renderEvent(event, ctx.x, offsetY);
          g.addChild(eventG);
        }
      }
    }
    return g;
  }

  private renderMilestone(milestone: EventWithDrawInfo, x: number, y: number) {
    const start = toDate(milestone.userObject.start);

    const milestoneG = new G({
      id: milestone.drawInfo!.id,
      class: 'event milestone',
    });

    const rectX = x + this.timeline.positionDate(start);
    const rectY = y;
    const rectWidth = this.eventHeight;
    const rectHeight = this.eventHeight;

    // Allow event-specific style overrides
    const bgColor = milestone.userObject.backgroundColor || this.style.backgroundColor;
    const fgColor = milestone.userObject.foregroundColor || this.style.textColor;
    const borderColor = milestone.userObject.borderColor || this.style.borderColor;
    let borders = milestone.userObject.borders;
    if (borders === undefined) {
      borders = this.style.borders;
    }

    const r = this.eventHeight / 2;
    const milestoneBg = new Path({
      d: `M${rectX},${rectY} l${r},${r} l-${r},${r} l-${r},-${r} l${r},-${r}`,
      'stroke-width': 1,
      fill: bgColor,
    });
    if (borders === true) {
      milestoneBg.setAttribute('stroke', borderColor);
    }
    milestoneG.addChild(milestoneBg);

    const title = milestone.userObject.title;
    if (title) {
      if (milestone.userObject.tooltip) {
        milestoneG.addChild(new Title({}, milestone.userObject.tooltip));
      }
      const fm = this.timeline.getFontMetrics(title, this.style.textSize);

      const textX = rectX + rectWidth - (this.eventHeight / 2) + this.style.eventLeftMargin;
      const textY = rectY + (this.eventHeight / 2);

      // Invisible rect with same width as outside text. Primary use is to catch
      // mouse interactions over the full rect height, rather than the text height
      milestoneG.addChild(new Rect({
        x: textX,
        y: rectY,
        width: fm.width,
        height: rectHeight,
        opacity: 0,
      }));

      milestoneG.addChild(new Text({
        x: textX,
        y: textY,
        fill: fgColor,
        'pointer-events': 'none',
        'text-anchor': 'left',
        'dominant-baseline': 'middle',
        'font-size': this.style.textSize,
      }, title));
    }

    if (this.opts.interactive) {
      milestoneG.setAttribute('cursor', this.style.highlightCursor);
      milestoneG.addChild(new Set({
        attributeName: 'opacity',
        to: this.style.highlightOpacity,
        begin: 'mouseover',
        end: 'mouseout',
      }));
    }

    return milestoneG;
  }

  test = 0;

  private renderEvent(event: EventWithDrawInfo, x: number, y: number) {
    const start = toDate(event.userObject.start);
    const stop = toDate(event.userObject.stop);

    const eventG = new G({
      id: event.drawInfo!.id,
      class: 'event',
    });

    const rectX = x + this.timeline.positionDate(start);
    const rectY = y;
    const rectWidth = this.timeline.pointsBetween(start, stop);
    const rectHeight = this.eventHeight;

    // Allow event-specific style overrides
    const bgColor = event.userObject.backgroundColor || this.style.backgroundColor;
    const fgColor = event.userObject.foregroundColor || this.style.textColor;
    const borderColor = event.userObject.borderColor || this.style.borderColor;
    const textAlign = event.userObject.textAlign || this.style.textAlign;
    const borders = event.userObject.borders || this.style.borders;

    // Background
    const eventBg = new Rect({
      x: rectX,
      y: rectY,
      width: rectWidth,
      height: rectHeight,
      fill: bgColor,
      rx: this.style.cornerRadius,
    });
    eventG.addChild(eventBg);

    if (borders === true) {
      eventBg.setAttribute('stroke', borderColor);
    } else if (borders === 'vertical') {
      eventG.addChild(new Path({
        d: `M${rectX},${rectY} l0,${rectHeight} M${rectX + rectWidth},${rectY} l0,${rectHeight}`,
        stroke: borderColor,
      }));
    }

    if (event.userObject.tooltip) {
      eventG.addChild(new Title({}, event.userObject.tooltip));
    }

    let title = event.userObject.title;
    if (title) {
      if (event.drawInfo!.offscreenStart) {
        title = '◀' + title;
      }
      const fm = this.timeline.getFontMetrics(title, this.style.textSize);
      const titleFitsInBox = fm.width <= event.drawInfo!.availableTitleWidth;

      if (event.drawInfo!.textOutside) {
        const textX = rectX + rectWidth + this.style.eventLeftMargin;
        const textY = rectY + (this.eventHeight / 2);

        // Invisible rect with same width as outside text. Primary use is to catch
        // mouse interactions over the full rect height, rather than the text height
        eventG.addChild(new Rect({
          x: textX,
          y: rectY,
          width: fm.width,
          height: rectHeight,
          opacity: 0,
        }));

        eventG.addChild(new Text({
          x: textX,
          y: textY,
          fill: fgColor,
          'pointer-events': 'none',
          'text-anchor': 'left',
          'dominant-baseline': 'middle',
          'font-size': this.style.textSize,
        }, title));
      } else if (this.opts.wrap || titleFitsInBox) { // Render text inside box
        // A clipPath for the text, with same dimensions as background
        const pathId = generateId();
        eventG.addChild(new ClipPath({ id: pathId }).addChild(
          new Rect({
            x: rectX,
            y: rectY,
            width: rectWidth,
            height: rectHeight,
          }),
        ));

        let textX = rectX;
        const centerAsked = textAlign === 'center';

        // Ensure title is fully visible on screen
        if (event.drawInfo!.offscreenStart) {
          textX = Math.max(textX, -this.timeline.xTranslation);
        }

        // Clipped short name
        if (centerAsked && !event.drawInfo!.offscreenStart) {
          eventG.addChild(new Text({
            x: textX + (rectWidth / 2),
            y: rectY + (this.eventHeight / 2),
            fill: fgColor,
            'pointer-events': 'none',
            'text-anchor': 'middle',
            'dominant-baseline': 'middle',
            'font-size': this.style.textSize,
            'clip-path': `url(#${pathId})`,
          }, title));
        } else {
          eventG.addChild(new Text({
            x: textX + this.style.eventLeftMargin,
            y: rectY + (this.eventHeight / 2),
            fill: fgColor,
            'pointer-events': 'none',
            'text-anchor': 'left',
            'dominant-baseline': 'middle',
            'font-size': this.style.textSize,
            'clip-path': `url(#${pathId})`,
          }, title));
        }
      }
    }

    if (this.opts.interactive) {
      if (this.opts.resizable) {
        eventG.addChild(new Ellipse({
          cx: rectX,
          cy: rectY + (this.eventHeight / 2),
          rx: 3,
          ry: 3,
          fill: 'white',
          stroke: 'black',
          'stroke-width': 0.5,
          cursor: 'w-resize',
        }));
        eventG.addChild(new Ellipse({
          cx: rectX + rectWidth,
          cy: rectY + (this.eventHeight / 2),
          rx: 3,
          ry: 3,
          fill: 'white',
          stroke: 'black',
          'stroke-width': 0.5,
          cursor: 'e-resize',
        }));
      }
      eventG.setAttribute('cursor', this.style.highlightCursor);
      eventG.addChild(new Set({
        attributeName: 'opacity',
        to: this.style.highlightOpacity,
        begin: 'mouseover',
        end: 'mouseout',
      }));
    }

    return eventG;
  }

  renderViewportXOverlay(ctx: RenderContext) {
    const g = new G();
    if (this.opts.leakEventBackground) {
      // Incoming ctx is not very useful as an origin
      // TODO try to fix the need for this xOffset
      const xOffset = this.timeline.getSidebarWidth() - ctx.translation.x;

      for (const line of this.lines) {
        for (const event of line) {
          const start = toDate(event.userObject.start);
          const stop = toDate(event.userObject.stop);
          const startX = this.timeline.positionDate(start);

          // Don't colorize on top of the sidebar
          if (event.drawInfo!.milestone) {
            g.addChild(new Line({
              x1: xOffset + startX,
              y1: 0,
              x2: xOffset + startX,
              y2: ctx.totalHeight,
              stroke: event.userObject.backgroundColor || this.style.backgroundColor,
              'stroke-width': 1,
              'stroke-opacity': 0.3,
              'pointer-events': 'none',
            }));
          } else {
            g.addChild(new Rect({
              x: xOffset + startX,
              y: 0,
              width: this.timeline.pointsBetween(start, stop),
              height: ctx.totalHeight,
              fill: event.userObject.backgroundColor || this.style.backgroundColor,
              'fill-opacity': 0.1,
              'pointer-events': 'none',
            }));
          }
        }
      }
    }
    return g;
  }

  onAction(id: string, action: Action) {
    super.onAction(id, action);
    if (this.eventsById[id]) {
      const userObject = this.eventsById[id].userObject;
      switch (action.type) {
        case 'click':
          const eventClickEvent = new EventEvent(userObject, action);
          this.timeline.fireEvent('eventClick', eventClickEvent);
          break;
        case 'contextmenu':
          const eventContextMenuEvent = new EventEvent(userObject, action);
          this.timeline.fireEvent('eventContextMenu', eventContextMenuEvent);
          break;
        case 'grabstart':
          this.handleDragStart(this.eventsById[id], action);
          break;
        case 'grabmove':
          this.handleDrag(this.eventsById[id], action);
          break;
        case 'grabend':
          this.handleDragEnd(this.eventsById[id], action);
          break;
        case 'mouseenter':
          if (!action.grabbing) {
            const mouseEnterEvent = new EventEvent(userObject, action);
            this.timeline.fireEvent('eventMouseEnter', mouseEnterEvent);
          }
          break;
        case 'mousemove':
          const mouseMoveEvent = new EventEvent(userObject, action);
          this.timeline.fireEvent('eventMouseMove', mouseMoveEvent);
          break;
        case 'mouseleave':
          const mouseLeaveEvent = new EventEvent(userObject, action);
          this.timeline.fireEvent('eventMouseLeave', mouseLeaveEvent);
          break;
      }
    }
  }

  private handleDragStart(event: EventWithDrawInfo, action: Action) {
    this.grabStart = action.grabStart!;

    /// There may already be an active transform on the element - add it to this drag start.
    const el = action.target! as SVGGElement;
    if (el.transform.baseVal.numberOfItems) {
      const transform = el.transform.baseVal.getItem(0);
      this.grabStart = this.grabStart.minus(new Point(transform.matrix.e, transform.matrix.f));
    }
  }

  private handleDrag(event: EventWithDrawInfo, action: Action) {
    if (this.grabStart) {
      const pt = action.grabPosition!.minus(this.grabStart);
      action.target!.setAttribute('transform', `translate(${pt.x}, ${pt.y})`);

      // this.prepareDrawOperation(this.opts.events || []);
      // this.timeline.rebuildDOM();
    }
  }

  private handleDragEnd(event: EventWithDrawInfo, action: Action) {
    if (this.grabStart) {
      const pt = action.grabPosition!.minus(this.grabStart);
      action.target!.setAttribute('transform', `translate(${pt.x}, ${pt.y})`);

      const changedEvent = new EventChangedEvent(event.userObject, action);

      const xDelta = action.grabPosition!.x - this.grabStart.x - this.timeline.xTranslation;

      const eventStart = this.timeline.positionDate(toDate(event.userObject.start));
      changedEvent.start = this.timeline.toDate(eventStart + xDelta);

      if (event.userObject.stop) {
        const eventStop = this.timeline.positionDate(toDate(event.userObject.stop));
        changedEvent.stop = this.timeline.toDate(eventStop + xDelta);
      }

      this.timeline.fireEvent('eventChanged', changedEvent);
    }
  }
}
