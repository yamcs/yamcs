import { Range } from './Range';

/**
 * Marker interface for all timelineStuff events
 */
export interface TimelineEvent {
}

export interface TimelineEventMap {
  'loadRange': LoadRangeEvent;
  'eventClick': EventEvent;
  'eventContextMenu': EventEvent;
  'eventMouseEnter': EventEvent;
  'eventMouseMove': EventEvent;
  'eventMouseLeave': EventEvent;
  'rangeSelectionChanged': RangeSelectionChangedEvent;
  'sidebarClick': SidebarClickEvent;
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

export class SidebarClickEvent implements TimelineEvent {

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

export class RangeSelectionChangedEvent implements TimelineEvent {

  constructor(readonly range?: Range) {
  }
}

export class EventEvent implements TimelineEvent {

  clientX: number;
  clientY: number;

  constructor(readonly userObject: object, readonly target?: Element) {
  }
}
