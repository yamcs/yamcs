import { Observable } from 'rxjs';
import { AlarmRange, ArgumentAssignment, NamedObjectId, Parameter } from './mdb';

export interface Value {
  type: 'AGGREGATE'
  | 'ARRAY'
  | 'BINARY'
  | 'BOOLEAN'
  | 'DOUBLE'
  | 'FLOAT'
  | 'SINT32'
  | 'SINT64'
  | 'STRING'
  | 'TIMESTAMP'
  | 'UINT32'
  | 'UINT64';
  aggregateValue?: AggregateValue;
  arrayValue?: Value[];
  binaryValue?: string;
  booleanValue?: boolean;
  doubleValue?: number;
  floatValue?: number;
  sint32Value?: number;
  sint64Value?: number;
  stringValue?: string;
  timestampValue?: number;
  uint32Value?: number;
  uint64Value?: number;
}

export interface AggregateValue {
  name: string[];
  value: Value[];
}

export type EventSeverity =
  'INFO' | 'WARNING' | 'ERROR' |
  'WATCH' | 'DISTRESS' | 'CRITICAL' | 'SEVERE'
  ;

export type MonitoringResult = 'DISABLED'
  | 'IN_LIMITS'
  | 'WATCH'
  | 'WARNING'
  | 'DISTRESS'
  | 'CRITICAL'
  | 'SEVERE';

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
  numericId: number;
  id: NamedObjectId;
  rawValue: Value;
  engValue: Value;
  acquisitionTimeUTC: string;
  generationTimeUTC: string;
  expirationTimeUTC: string;

  acquisitionStatus: 'ACQUIRED' | 'NOT_RECEIVED' | 'INVALID' | 'EXPIRED';
  processingStatus: boolean;
  monitoringResult: MonitoringResult;
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
  useNumericIds?: boolean;
}

export interface ParameterSubscriptionResponse {
  subscriptionId: number;
  subscribed: SubscribedParameter[];
  invalid: NamedObjectId[];
  mapping: { [key: number]: NamedObjectId };
  parameterValues$: Observable<ParameterValue[]>;
}

export interface SubscribedParameter {
  id: NamedObjectId;
  numericId: number;
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

export interface ManagementSubscriptionRequest {
  clientInfo?: boolean;
  processorStatistics?: boolean;
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

export interface IssueCommandOptions {
  origin?: string;
  sequenceNumber?: number;
  dryRun?: boolean;
  assignment?: ArgumentAssignment[];
  comment?: string;
}

export interface IssueCommandResponse {
  commandQueueEntry: CommandQueueEntry;
  source: string;
  hex: string;
  binary: string;
}

export interface CommandHistoryAttribute {
  name: string;
  value: Value;
}

export interface CommandAssignment {
  name: string;
  value: Value;
  userInput: boolean;
}

export interface CommandHistoryEntry {
  commandId: CommandId;
  generationTimeUTC: string;
  attr: CommandHistoryAttribute[];
  assignment: CommandAssignment[];
}

export interface CommandHistoryPage {
  entry?: CommandHistoryEntry[]
  continuationToken?: string;
}

export interface Alarm {
  seqNum: number;
  type: 'ACTIVE' | 'TRIGGERED' | 'SEVERITY_INCREASED' | 'PVAL_UPDATED' | 'ACKNOWLEDGED' | 'CLEARED';
  triggerValue: ParameterValue;
  mostSevereValue: ParameterValue;
  currentValue: ParameterValue;
  violations: number;
  valueCount: number;
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

export interface EditAlarmOptions {
  state: 'acknowledged' | 'unacknowledged';
  comment?: string;
}

export interface GetCommandHistoryOptions {
  start?: string;
  stop?: string;
  pos?: number;
  limit?: number;
  next?: string;
  q?: string;
  order?: 'asc' | 'desc';
}

export interface CreateEventRequest {
  message: string;
  type?: string;
  severity?: EventSeverity;
  time?: string;
}

export interface CreateProcessorRequest {
  name: string;
  type: string;
  clientId?: number[];
  persistent?: boolean;
  config?: string;
}

export interface EditReplayProcessorRequest {
  state?: 'running' | 'paused';
  seek?: string;
  speed?: string;
}

export interface GetEventsOptions {
  /**
   * Inclusive lower bound
   */
  start?: string;
  /**
   * Exclusive upper bound
   */
  stop?: string;
  /**
   * Search string
   */
  q?: string;
  severity?: EventSeverity;
  source?: string | string[];
  pos?: number;
  limit?: number;
  order?: 'asc' | 'desc';
}

export interface DownloadEventsOptions {
  /**
   * Inclusive lower bound
   */
  start?: string;
  /**
   * Exclusive upper bound
   */
  stop?: string;
  /**
   * Search string
   */
  q?: string;
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

export interface BatchDownloadParameterValuesOptions {
  parameters?: string | string[];
  start?: string;
  stop?: string;
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

export interface GetPacketIndexOptions {
  start?: string;
  stop?: string;
  mergeTime?: number;
  limit?: number;
}

export interface GetCompletenessIndexOptions {
  start?: string;
  stop?: string;
  limit?: number;
}

export interface DownloadPacketsOptions {
  /**
   * Inclusive lower bound
   */
  start?: string;
  /**
   * Exclusive upper bound
   */
  stop?: string;
  name?: string | string[];
  format?: 'raw';
  order?: 'asc' | 'desc';
}

export interface IndexGroup {
  id: NamedObjectId;
  entry: IndexEntry[];
}

export interface IndexEntry {
  start: string;
  stop: string;
  count: number;
}

export interface CommandQueueEntry {
  instance: string;
  processorName: string;
  queueName: string;
  cmdId: CommandId;
  source: string;
  binary: string;
  username: string;
  generationTimeUTC: string;
  uuid: string;
}

export interface GetTagsOptions {
  start?: string;
  stop?: string;
}

export interface TagsPage {
  tag: ArchiveTag[];
}

export interface ArchiveTag {
  id: number;
  name: string;
  startUTC: string;
  stopUTC: string;
  description: string;
  color: string;
}
