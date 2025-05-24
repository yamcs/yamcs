import { Activity } from './activities';

export interface SaveTimelineItemRequest {
  name: string;
  start: string;
  duration: string;
  type: TimelineItemType;
  tags?: string[];
  properties?: { [key: string]: string };
  activityDefinition?: ActivityDefinition;
  autoStart?: boolean;
}

export type TimelineItemType = 'EVENT' | 'ACTIVITY';

export type ItemExecutionStatus =
  | 'PLANNED'
  | 'READY'
  | 'WAITING_ON_DEPENDENCY'
  | 'IN_PROGRESS'
  | 'SUCCEEDED'
  | 'ABORTED'
  | 'FAILED'
  | 'SKIPPED'
  | 'CANCELED'
  | 'PAUSED'
  | 'WAITING_FOR_INPUT';

export interface TimelineItem {
  id: string;
  name: string;
  start: string;
  duration: string;
  type: TimelineItemType;
  tags?: string[];
  properties?: { [key: string]: string };
  status?: ItemExecutionStatus;
  failureReason?: string;
  activityDefinition?: ActivityDefinition;
  predecessors?: Predecessor[];
  activityRuns?: Activity[];
  autoStart?: boolean;
}

export type StartCondition =
  | 'ON_COMPLETION'
  | 'ON_SUCCESS'
  | 'ON_FAILURE'
  | 'ON_START';

export interface Predecessor {
  itemId: string;
  name?: string;
  startCondition: StartCondition;
}

export interface ActivityDefinition {
  type: string;
  args?: { [key: string]: any };
  description?: string;
}

export interface TimelineViewsPage {
  views: TimelineView[];
}

export interface TimelineBandsPage {
  bands: TimelineBand[];
}

export interface TimelineTagsPage {
  tags: string[];
}

export interface TimelineItemsPage {
  items: TimelineItem[];
}

export interface GetTimelineItemsOptions {
  source: string;
  start?: string;
  stop?: string;
  band?: string;
  filter?: string;
  details?: boolean;
}

export type TimelineBandType =
  | 'EXECUTED_ACTIVITIES'
  | 'TIME_RULER'
  | 'ITEM_BAND'
  | 'SPACER'
  | 'COMMAND_BAND'
  | 'PARAMETER_PLOT'
  | 'PARAMETER_STATES'
  | 'PLANNED_ACTIVITIES'
  | 'CONNECTED_ITEM_BAND';

export interface SaveTimelineBandRequest {
  name: string;
  description?: string;
  type: TimelineBandType;
  shared: boolean;
  tags?: string[];
  properties?: { [key: string]: string };
}

export interface TimelineBand {
  id: string;
  type: TimelineBandType;
  shared: boolean;
  name: string;
  description?: string;
  tags?: string[];
  properties?: { [key: string]: string };
  username: string;
}

export interface TimelineView {
  id: string;
  name: string;
  bands?: TimelineBand[];
}

export interface CreateTimelineViewRequest {
  name: string;
  bands?: string[];
}

export interface UpdateTimelineViewRequest {
  name: string;
  bands?: string[];
}
