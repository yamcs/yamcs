import {
  Command,
  Instance,
  Parameter,
  Record,
  Service,
  Stream,
  Table,
} from './main';

export interface InstancesWrapper {
  instance: Instance[];
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
