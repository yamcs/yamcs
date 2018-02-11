import { Alias, AlarmRange } from './mdb';

export interface GeneralInfo {
  yamcsVersion: string;
  serverId: string;
  defaultYamcsInstance: string;
}

export interface Instance {
  name: string;
  state: string;
  processor: Processor[];
}

export interface Service {
  instance: string;
  name: string;
  state: string;
  className: string;
}

export interface Link {
  instance: string;
  name: string;
  type: string;
  spec: string;
  stream: string;
  disabled: boolean;
  dataCount: number;
  status: string;
  detailedStatus: string;
}

export interface Stream {
  name: string;
  column: Column[];
}

export interface Column {
  name: string;
  type: string;
}

export interface Table {
  name: string;
  keyColumn: Column[];
  valueColumn: Column[];
}

export interface Record {
  column: ColumnData[];
}

export interface ColumnData {
  name: string;
  value: Value;
}

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

export interface Processor {
  name: string;
}

export interface ClientInfo {
  instance: string;
  id: number;
  username: string;
  applicationName: string;
  processorName: string;
  state: 'CONNECTED' | 'DISCONNECTED';
  currentClient: boolean;
  loginTimeUTC: string;
}

export interface DisplayInfo {
  folder: DisplayFolder[];
  file: DisplayFile[];
}

export interface DisplayFolder {
  filename: string;
  folder: DisplayFolder[];
  file: DisplayFile[];
}

export interface DisplayFile {
  filename: string;
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

export interface LinkEvent {
  type: string;
  linkInfo: Link;
}

export interface ParameterData {
  parameter: ParameterValue[];
}

export interface ParameterValue {
  id: Alias;
  rawValue: any;
  engValue: any;
  acquisitionTimeUTC: string;
  generationTimeUTC: string;
  expirationTimeUTC: string;

  acquisitionStatus: any;
  processingStatus: boolean;
  monitoringResult: any;
  alarmRange: AlarmRange[];
  rangeCondition?: 'LOW' | 'HIGH';
  expireMillis: number;
}

export interface ParameterSubscriptionRequest {
  id: Alias[];
  abortOnInvalid: boolean;
  updateOnExpiration: boolean;
  sendFromCache: boolean;
}

export interface GetParametersOptions {
  namespace?: string;
  recurse?: boolean;
  type?: string;
  q?: string;
}

export interface GetAlgorithmsOptions {
  namespace?: string;
  recurse?: boolean;
  q?: string;
}

export interface GetContainersOptions {
  namespace?: string;
  recurse?: boolean;
  q?: string;
}

export interface GetCommandsOptions {
  namespace?: string;
  recurse?: boolean;
  q?: string;
}
