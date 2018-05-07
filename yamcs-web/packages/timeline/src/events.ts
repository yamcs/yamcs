import { Action } from './Action';
import { Range } from './Range';

/**
 * Marker interface for all Timeline events
 */
export interface TimelineEvent {
}

export interface TimelineEventMap {
  'loadRange': LoadRangeEvent;
  'eventClick': EventEvent;
  'eventContextMenu': EventEvent;
  'eventChanged': EventChangedEvent;
  'eventMouseEnter': EventEvent;
  'eventMouseMove': EventEvent;
  'eventMouseLeave': EventEvent;
  'grabStart': GrabStartEvent;
  'grabEnd': GrabEndEvent;
  'rangeSelectionChanged': RangeSelectionChangedEvent;
  'sidebarClick': SidebarEvent;
  'sidebarContextMenu': SidebarEvent;
  'sidebarMouseEnter': SidebarEvent;
  'sidebarMouseLeave': SidebarEvent;
  'viewportHover': ViewportHoverEvent;
  'viewportChange': ViewportChangeEvent;
  'viewportChanged': ViewportChangedEvent;
  'viewportWheel': WheelViewportEvent;
}

export type TimelineEventHandlers = {
  [t in keyof TimelineEventMap]: Array<(ev: TimelineEventMap[t]) => void>
};

export class LoadRangeEvent implements TimelineEvent {

  constructor(readonly loadStart: Date, readonly loadStop: Date) {
  }
}

export class ViewportHoverEvent implements TimelineEvent {

  constructor(readonly date?: Date, readonly x?: number) {
  }
}

export class SidebarEvent implements TimelineEvent {

  clientX: number;
  clientY: number;

  constructor(readonly userObject: object, readonly target?: Element) {
  }
}

export class WheelViewportEvent implements TimelineEvent {

  readonly target: EventTarget | null;
  readonly wheelDelta: number;

  constructor(originalEvent: WheelEvent) {
    this.target = originalEvent.target;
    this.wheelDelta = originalEvent.wheelDelta;
  }
}

export class ViewportChangeEvent implements TimelineEvent {
}

export class ViewportChangedEvent implements TimelineEvent {
}

export class GrabStartEvent implements TimelineEvent {
}

export class GrabEndEvent implements TimelineEvent {
}

export class RangeSelectionChangedEvent implements TimelineEvent {

  constructor(readonly range?: Range) {
  }
}

export class EventEvent implements TimelineEvent {

  target?: Element;
  clientX: number;
  clientY: number;

  constructor(readonly userObject: object, action: Action) {
    this.target = action.target;
    this.clientX = action.clientX;
    this.clientY = action.clientY;
  }
}

export class EventChangedEvent extends EventEvent {

  start: Date;
  stop?: Date;

  constructor(readonly userObject: object, action: Action) {
    super(userObject, action);
  }
}
