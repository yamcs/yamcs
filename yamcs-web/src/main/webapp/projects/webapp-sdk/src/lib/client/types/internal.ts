import { Alarm } from './alarms';
import { Event } from './events';
import { Link } from './management';
import { IndexGroup, Range, Sample } from './monitoring';
import { Processor } from './processing';
import { CommandQueue } from './queue';
import { Bucket, GroupInfo, Instance, InstanceTemplate, RocksDbDatabase, RoleInfo, Service, SessionInfo, UserInfo } from './system';
import { Record, Stream, Table } from './table';

export interface EventsWrapper {
  events: Event[];
}

export interface InstancesWrapper {
  instances: Instance[];
}

export interface InstanceTemplatesWrapper {
  templates: InstanceTemplate[];
}

export interface LinksWrapper {
  links: Link[];
}

export interface ServicesWrapper {
  services: Service[];
}

export interface AlarmsWrapper {
  alarms: Alarm[];
}

export interface UsersWrapper {
  users: UserInfo[];
}

export interface GroupsWrapper {
  groups: GroupInfo[];
}

export interface RolesWrapper {
  roles: RoleInfo[];
}

export interface SessionsWrapper {
  sessions: SessionInfo[];
}

export interface CommandQueuesWrapper {
  queues: CommandQueue[];
}

export interface ProcessorsWrapper {
  processors: Processor[];
}

export interface StreamsWrapper {
  streams: Stream[];
}

export interface TablesWrapper {
  tables: Table[];
}

export interface RecordsWrapper {
  record: Record[];
}

export interface SamplesWrapper {
  sample: Sample[];
}

export interface RangesWrapper {
  range: Range[];
}

export interface SourcesWrapper {
  sources: string[];
}

export interface IndexResult {
  group: IndexGroup[];
}

export interface BucketsWrapper {
  buckets: Bucket[];
}

export interface RocksDbDatabasesWrapper {
  databases: RocksDbDatabase[];
}
