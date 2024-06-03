import { WebSocketCall } from '../WebSocketCall';

export type ActivityStatus = 'RUNNING' | 'SUCCESSFUL' | 'CANCELLED' | 'FAILED';

export interface Activity {
  type: string;
  args: { [key: string]: any; };
  detail: string;
  id: string;
  start: string;
  seq: number;
  status: string;
  startedBy: string;
  stop?: string;
  stoppedBy?: string;
  failureReason?: string;
}

export interface ActivitiesPage {
  activities: Activity[];
  continuationToken?: string;
}

export interface GetActivityLogResponse {
  logs: ActivityLog[];
}

export interface ActivityLog {
  time: string;
  source: string;
  level: string;
  message: string;
}

export interface ExecutorsWrapper {
  executors: Executor[];
}

export interface Executor {
  type: string;
  displayName: string;
  description?: string;
  icon?: string;
}

export interface ActivityScriptsPage {
  scripts: string[];
}

export interface SubscribeActivitiesRequest {
  instance: string;
}

export interface SubscribeActivityLogRequest {
  instance: string;
  activity: string;
}

export type GlobalActivityStatusSubscription = WebSocketCall<SubscribeGlobalActivityStatusRequest, GlobalActivityStatus>;
export type ActivitySubscription = WebSocketCall<SubscribeActivitiesRequest, Activity>;
export type ActivityLogSubscription = WebSocketCall<SubscribeActivityLogRequest, ActivityLog>;

export class StartActivityOptions {
  type: string;
  args?: { [key: string]: any; };
  comment?: string;
}

export class CompleteManualActivityOptions {
  failureReason?: string;
}

export interface SubscribeGlobalActivityStatusRequest {
  instance: string;
}

export interface GlobalActivityStatus {
  ongoingCount: number;
}

export interface GetActivitiesOptions {
  /**
   * Inclusive lower bound
   */
  start?: string;
  /**
   * Exclusive upper bound
   */
  stop?: string;
  status?: string | string[];
  q?: string;
  type?: string | string[];
  limit?: number;
  order?: 'asc' | 'desc';
  next?: string;
}
