import { WebSocketCall } from '../WebSocketCall';
import { NamedObjectId, Parameter } from './mdb';
import { ParameterValue } from './monitoring';

export interface GlobalAlarmStatus {
  unacknowledgedCount: number;
  unacknowledgedActive: boolean;
  acknowledgedCount: number;
  acknowledgedActive: boolean;
  shelvedCount: number;
  shelvedActive: boolean;
}

export interface SubscribeGlobalAlarmStatusRequest {
  instance: string;
  processor: string;
}

export interface SubscribeAlarmsRequest {
  instance: string;
  processor: string;
}

export interface ListAlarmsResponse {
  alarms: Alarm[];
}

export type AlarmNotificationType = 'ACTIVE'
  | 'TRIGGERED'
  | 'SEVERITY_INCREASED'
  | 'VALUE_UPDATED'
  | 'ACKNOWLEDGED'
  | 'CLEARED'
  | 'RTN'
  | 'SHELVED'
  | 'UNSHELVED'
  | 'RESET'
  ;

export type AlarmSeverity = 'WATCH'
  | 'WARNING'
  | 'DISTRESS'
  | 'CRITICAL'
  | 'SEVERE'
  ;

export interface Alarm {
  seqNum: number;
  type: 'EVENT' | 'PARAMETER';
  notificationType: AlarmNotificationType;
  id: NamedObjectId;
  triggerTime: string;
  violations: number;
  count: number;
  acknowledgeInfo: AcknowledgeInfo;
  shelveInfo: ShelveInfo;
  clearInfo: ClearInfo;
  severity: AlarmSeverity;

  latching: boolean;
  processOK: boolean;
  triggered: boolean;
  acknowledged: boolean;

  parameterDetail?: ParameterAlarmData;
  eventDetail?: EventAlarmData;
}

export interface ParameterAlarmData {
  triggerValue: ParameterValue;
  mostSevereValue: ParameterValue;
  currentValue: ParameterValue;
  parameter: Parameter;
}

export interface EventAlarmData {
  triggerEvent: Event;
  mostSevereEvent: Event;
  currentEvent: Event;
}

export interface AcknowledgeInfo {
  acknowledgedBy: string;
  acknowledgeMessage: string;
  acknowledgeTime: string;
}

export interface ShelveInfo {
  shelvedBy: string;
  shelveMessage: string;
  shelveTime: string;
  shelveExpiration: string;
}

export interface ClearInfo {
  clearedBy: string;
  clearTime: string;
  clearMessage: string;
}

export interface GetAlarmsOptions {
  start?: string;
  stop?: string;
  detail?: boolean;
  pos?: number;
  limit?: number;
  order?: 'asc' | 'desc';
}

export interface EditAlarmOptions {
  state: 'acknowledged' | 'shelved' | 'unshelved' | 'cleared';
  comment?: string;
  shelveDuration?: number;
}

export type GlobalAlarmStatusSubscription = WebSocketCall<SubscribeGlobalAlarmStatusRequest, GlobalAlarmStatus>;
export type AlarmSubscription = WebSocketCall<SubscribeAlarmsRequest, Alarm>;
