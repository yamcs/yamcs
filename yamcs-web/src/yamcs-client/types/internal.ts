import {
  Event,
  Instance,
  Link,
  Record,
  Service,
  Stream,
  Table,
} from './main';

import {
  Algorithm,
  Command,
  Container,
  Parameter,
  SpaceSystem,
} from './mdb';

export type WebSocketClientMessage = [
  number, // Protocol
  number, // Message Type
  number, // Request Sequence
  { [key: string]: string } // payload
];

export type WebSocketServerMessage = [
  number, // Protocol
  number, // Message Type
  number, // Response Sequence
  {
    dt?: string
    data?: { [key: string]: any }

    et?: string
    msg?: string
  }
];

export interface EventsWrapper {
  event: Event[];
}

export interface ContainersWrapper {
  container: Container[];
}

export interface InstancesWrapper {
  instance: Instance[];
}

export interface LinksWrapper {
  link: Link[];
}

export interface ServicesWrapper {
  service: Service[];
}

export interface SpaceSystemsWrapper {
  spaceSystem: SpaceSystem[];
}

export interface AlgorithmsWrapper {
  algorithm: Algorithm[];
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
