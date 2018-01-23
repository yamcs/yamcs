import {
  Command,
  Instance,
  Link,
  Parameter,
  Record,
  Service,
  Stream,
  Table,
} from './main';

// Protocol, Message Type, Request Sequence, payload
export type WebSocketClientMessage = [number, number, number, {
  [key: string]: string
}];

// Protocol, Message Type, Response Sequence, payload
export type WebSocketServerMessage = [number, number, number, {
  dt: string
  data: {[key: string]: any}
}];

export interface InstancesWrapper {
  instance: Instance[];
}

export interface LinksWrapper {
  link: Link[];
}

export interface ServicesWrapper {
  service: Service[];
}

export interface ParametersWrapper {
  parameter: Parameter[];
}

export interface CommandsWrapper {
  command: Command[];
}

export interface StreamsWrapper {
  stream: Stream[];
}

export interface TablesWrapper {
  table: Table[];
}

export interface RecordsWrapper {
  record: Record[];
}
