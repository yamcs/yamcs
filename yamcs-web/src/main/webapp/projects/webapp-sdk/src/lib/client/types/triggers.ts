import { WebSocketCall } from '../WebSocketCall';
import { AlarmNotificationType, AlarmSeverity } from './alarms';
import { ActivityDefinition } from './timeline';

export type TriggerSource = 'DATABASE' | 'CONFIG';

export type TriggerAlarmType = 'PARAMETER' | 'EVENT';

export type TriggerEdge = 'RISING' | 'FALLING' | 'BOTH';

export type TriggerConcurrency = 'SKIP' | 'ALLOW' | 'CANCEL_PREVIOUS';

export type TriggerFiringOutcome =
  | 'STARTED'
  | 'SUPPRESSED_COOLDOWN'
  | 'SUPPRESSED_CONCURRENCY'
  | 'SUPPRESSED_RATE'
  | 'PENDING_CONFIRMATION'
  | 'REJECTED'
  | 'EXPIRED'
  | 'FAILED_TO_START';

export interface Trigger {
  id: string;
  name: string;
  description?: string;
  enabled: boolean;
  processor?: string;
  runAs?: string;
  owner?: string;
  source: TriggerSource;
  alarmCondition?: AlarmCondition;
  parameterCondition?: ParameterCondition;
  policy?: TriggerPolicy;
  activity: ActivityDefinition & { label?: string };
  lastFired?: string;
  firingCount?: number;
}

export interface AlarmCondition {
  alarmType?: TriggerAlarmType;
  subjects?: string[];
  notifications?: AlarmNotificationType[];
  minSeverity?: AlarmSeverity;
  includeShelved?: boolean;
}

export interface ParameterCondition {
  expression: string;
  edge?: TriggerEdge;
  /**
   * Hold time in milliseconds
   */
  holdTime?: number | string;
  invalidAsFalse?: boolean;
}

export interface TriggerPolicy {
  /**
   * Cooldown in milliseconds
   */
  cooldown?: number | string;
  concurrency?: TriggerConcurrency;
  requireConfirmation?: boolean;
  /**
   * Confirmation timeout in milliseconds
   */
  confirmationTimeout?: number | string;
  maxFiringsPerHour?: number;
}

export interface SaveTriggerRequest {
  name: string;
  description?: string;
  enabled?: boolean;
  processor?: string;
  runAs?: string;
  alarmCondition?: AlarmCondition;
  parameterCondition?: ParameterCondition;
  policy?: TriggerPolicy;
  activity: ActivityDefinition & { label?: string };
}

export interface ListTriggersResponse {
  triggers?: Trigger[];
}

export interface TriggerServiceStatus {
  enabled: boolean;
  pendingConfirmations?: number;
}

export interface TestTriggerRequest {
  context?: { [key: string]: any };
}

export interface TestTriggerResponse {
  activity?: ActivityDefinition & { label?: string };
  error?: string;
}

export interface TriggerFiring {
  id: string;
  triggerId: string;
  triggerName: string;
  time: string;
  outcome: TriggerFiringOutcome;
  context?: { [key: string]: any };
  activityId?: string;
  decidedBy?: string;
  message?: string;
}

export interface GetTriggerFiringsOptions {
  /**
   * Trigger identifier or name
   */
  trigger?: string;
  outcome?: TriggerFiringOutcome | TriggerFiringOutcome[];
  /**
   * Inclusive lower bound
   */
  start?: string;
  /**
   * Exclusive upper bound
   */
  stop?: string;
  limit?: number;
}

export interface ListTriggerFiringsResponse {
  firings?: TriggerFiring[];
}

export interface SubscribeTriggerFiringsRequest {
  instance: string;
}

export type TriggerFiringSubscription = WebSocketCall<
  SubscribeTriggerFiringsRequest,
  TriggerFiring
>;
