export interface CreateTimelineItemRequest {
  name: string;
  start: string;
  duration: string;
  type: TimelineItemType;
}

export interface UpdateTimelineItemRequest extends CreateTimelineItemRequest {
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

export type TimelineBandType = 'TIME_RULER' | 'ITEM_BAND';

export interface CreateTimelineBandRequest {
  name: string;
  type: TimelineBandType;
  shared: boolean;
  tags?: string[];
  properties?: { [key: string]: string; };
}

export interface TimelineBand {
  id: string;
  type: TimelineBandType,
  shared: boolean;
  name: string;
  tags?: string[];
  properties?: { [key: string]: string; };
  username: string;
}

export interface UpdateTimelineBandRequest {
  name: string;
  shared: boolean;
  tags?: string[];
  properties?: { [key: string]: string; };
}
