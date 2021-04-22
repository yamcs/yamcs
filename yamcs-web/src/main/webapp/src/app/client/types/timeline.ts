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
  uuid: string;
  name: string;
  start: string;
  duration: string;
  type: TimelineItemType;
}

export interface TimelineItemsPage {
  items: TimelineItem[];
}

export interface GetTimelineItemsOptions {
  type?: TimelineItemType;
}

export interface CreateTimelineBandRequest {
  name: string;
}

export interface TimelineBand {
  uuid: string;
  name: string;
}
