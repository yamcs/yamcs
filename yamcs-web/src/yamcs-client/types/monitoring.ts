import { Alias, AlarmRange } from './mdb';

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
}

export interface ParameterValue {
  id: Alias;
  rawValue: Value;
  engValue: Value;
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
