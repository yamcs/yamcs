import { WebSocketCall } from '../WebSocketCall';
import { Value } from './monitoring';

export interface SubscribeStreamStatisticsRequest {
  instance: string;
}

export interface SubscribeStreamRequest {
  instance: string;
  stream: string;
}

export interface StreamEvent {
  type: string;
  name: string;
  dataCount: number;
}

export interface Stream {
  name: string;
  columns: Column[];
  script: string;
  dataCount: number;
  subscribers: string[];
}

export interface StreamData {
  stream: string;
  column: ColumnData[];
}

export interface Column {
  name: string;
  type: string;
  enumValue: SQLEnumValue[];
  autoIncrement?: boolean;
}

export interface SQLEnumValue {
  value: number;
  label: string;
}

export interface Table {
  name: string;
  keyColumn: Column[];
  valueColumn: Column[];
  histogramColumn?: string[];
  storageEngine: string;
  formatVersion: number;
  tablespace?: string;
  compressed: boolean;
  partitioningInfo?: PartitioningInfo;
  script: string;
}

export interface PartitioningInfo {
  type: 'TIME' | 'VALUE' | 'TIME_AND_VALUE';
  timeColumn: string;
  timePartitionSchema: string;
  valueColumn: string;
  valueColumnType: string;
}

export interface Record {
  column: ColumnData[];
}

export interface ColumnData {
  name: string;
  value: Value;
}

export type StreamStatisticsSubscription = WebSocketCall<SubscribeStreamStatisticsRequest, StreamEvent>;
export type StreamSubscription = WebSocketCall<SubscribeStreamRequest, StreamData>;
