import { AlarmRange, Parameter, NamedObjectId } from './mdb';
import { Observable } from 'rxjs/Observable';

export interface Value {
  type: 'FLOAT'
  | 'DOUBLE'
  | 'UINT32'
  | 'SINT32'
  | 'BINARY'
  | 'STRING'
  | 'TIMESTAMP'
  | 'UINT64'
  | 'SINT64'
  | 'BOOLEAN';
  floatValue: number;
  doubleValue: number;
  sint32Value: number;
  uint32Value: number;
  binaryValue: string;
  stringValue: string;
  timestampValue: number;
  uint64Value: number;
  sint64Value: number;
  booleanValue: boolean;
}

export interface DisplayFolder {
  name: string;
  path: string;
  folder?: DisplayFolder[];
  file?: DisplayFile[];
}

export interface DisplayFile {
  name: string;
  path: string;
}

export type EventSeverity =
  'INFO' | 'WARNING' | 'ERROR' |
  'WATCH' | 'DISTRESS' | 'CRITICAL' | 'SEVERE'
  ;

export interface Event {
  source: string;
  generationTimeUTC: string;
  receptionTimeUTC: string;
  seqNumber: number;
  type: string;
  message: string;
  severity: EventSeverity;
}

export interface TimeInfo {
  currentTime: number;
  currentTimeUTC: string;
}

export interface ParameterData {
  parameter: ParameterValue[];
  subscriptionId: number;
}

export interface ParameterValue {
  id: NamedObjectId;
  rawValue: Value;
  engValue: Value;
  acquisitionTimeUTC: string;
  generationTimeUTC: string;
  expirationTimeUTC: string;

  acquisitionStatus: 'ACQUIRED' | 'NOT_RECEIVED' | 'INVALID' | 'EXPIRED';
  processingStatus: boolean;
  monitoringResult: any;
  alarmRange: AlarmRange[];
  rangeCondition?: 'LOW' | 'HIGH';
  expireMillis: number;
}

export interface ParameterSubscriptionRequest {
  id: NamedObjectId[];
  abortOnInvalid?: boolean;
  updateOnExpiration?: boolean;
  sendFromCache?: boolean;
  subscriptionId?: number;
}

export interface ParameterSubscriptionResponse {
  subscriptionId: number;
  invalid: NamedObjectId[];
  parameterValues$: Observable<ParameterValue[]>;
}

export interface EventSubscriptionResponse {
  event$: Observable<Event>;
}

export interface AlarmSubscriptionResponse {
  alarm$: Observable<Alarm>;
}

export interface TimeSubscriptionResponse {
  /**
   * Current Mission Time
   */
  timeInfo: TimeInfo;

  /**
   * Observable for monitoring Mission Time updates
   */
  timeInfo$: Observable<TimeInfo>;
}

export interface Sample {
  time: string;
  avg: number;
  min: number;
  max: number;
  n: number;
}

export interface Range {
  timeStart: string;
  timeStop: string;
  engValue: Value;
  count: number;
}

export interface CommandId {
  generationTime: number;
  origin: string;
  sequenceNumber: number;
  commandName: string;
}

export interface CommandHistoryAttribute {
  name: string;
  value: Value;
}

export interface CommandHistoryEntry {
  commandId: CommandId;
  generationTimeUTC: string;
  attr: CommandHistoryAttribute[];
}

export interface Alarm {
  seqNum: number;
  type: 'ACTIVE' | 'TRIGGERED' | 'SEVERITY_INCREASED' | 'PVAL_UPDATED' | 'ACKNOWLEDGED' | 'CLEARED';
  triggerValue: ParameterValue;
  mostSevereValue: ParameterValue;
  currentValue: ParameterValue;
  violations: number;
  acknowledgeInfo: AcknowledgeInfo;
  parameter: Parameter;
}

export interface AcknowledgeInfo {
  acknowledgedBy: string;
  acknowledgedMessage: string;
  acknowledgeTimeUTC: string;
}

export interface GetAlarmsOptions {
  start?: string;
  stop?: string;
  pos?: number;
  limit?: number;
  order?: 'asc' | 'desc';
}

export interface GetCommandHistoryOptions {
  start?: string;
  stop?: string;
  pos?: number;
  limit?: number;
  order?: 'asc' | 'desc';
}

export interface GetEventsOptions {
  start?: string;
  stop?: string;
  filter?: string;
  severity?: EventSeverity;
  source?: string | string[];
  pos?: number;
  limit?: number;
  order?: 'asc' | 'desc';
}

export interface DownloadEventsOptions {
  start?: string;
  stop?: string;
  filter?: string;
  severity?: EventSeverity;
  source?: string | string[];
  format?: 'csv';
  order?: 'asc' | 'desc';
}

export interface GetParameterValuesOptions {
  start?: string;
  stop?: string;
  pos?: number;
  limit?: number;
  norepeat?: boolean;
  format?: 'csv';
  order?: 'asc' | 'desc';
}

export interface DownloadParameterValuesOptions {
  start?: string;
  stop?: string;
  norepeat?: boolean;
  format?: 'csv';
  order?: 'asc' | 'desc';
}

export interface GetParameterSamplesOptions {
  start?: string;
  stop?: string;
  count?: number;
  order?: 'asc' | 'desc';
}

export interface GetParameterRangesOptions {
  start?: string;
  stop?: string;
  minGap?: number;
  maxGap?: number;
}
