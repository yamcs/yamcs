import { Value } from './monitoring';

export interface GeneralInfo {
  yamcsVersion: string;
  serverId: string;
  defaultYamcsInstance: string;
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

export interface Processor {
  instance: string;
  name: string;
  type: string;
  creator: string;
  hasAlarms: boolean;
  hasCommanding: boolean;
  state: string;
}

export interface LinkEvent {
  type: string;
  linkInfo: Link;
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
