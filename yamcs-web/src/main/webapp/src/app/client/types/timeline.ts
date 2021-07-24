export interface CreateTimelineItemRequest {
  name: string;
  start: string;
  duration: string;
  type: TimelineItemType;
}

export interface UpdateTimelineItemRequest {
  name?: string;
  start?: string;
  duration?: string;
}

export type TimelineItemType = 'EVENT' | 'MANUAL_ACTIVITY' | 'AUTO_ACTIVITY';

export interface TimelineItem {
  id: string;
  name: string;
  start: string;
  duration: string;
  type: TimelineItemType;
}

export interface TimelineBandsPage {
  bands: TimelineBand[];
}

export interface TimelineItemsPage {
  items: TimelineItem[];
}

export interface GetTimelineItemsOptions {
  type?: TimelineItemType;
}

export type TimelineBandType = 'TIME_RULER' | 'EVENT_BAND';

export interface CreateTimelineBandRequest {
  name: string;
  type: TimelineBandType;
  extra?: { [key: string]: string; };
}

export interface TimelineBand {
  id: string;
  type: TimelineBandType,
  name: string;
  extra?: { [key: string]: string; };
}
