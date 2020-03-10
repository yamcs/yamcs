import { Alarm } from './alarms';
import { Event } from './events';
import { Link } from './management';
import { SpaceSystem } from './mdb';
import { IndexGroup, Range, Sample } from './monitoring';
import { Processor } from './processing';
import { CommandQueue } from './queue';
import { Bucket, ClientConnectionInfo, GroupInfo, Instance, InstanceTemplate, RocksDbDatabase, RoleInfo, Service, UserInfo } from './system';
import { Record, Stream, Table } from './table';

export interface EventsWrapper {
  event: Event[];
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

export interface SpaceSystemsWrapper {
  spaceSystem: SpaceSystem[];
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

export interface ClientConnectionsWrapper {
  connections: ClientConnectionInfo[];
}

export interface CommandQueuesWrapper {
  queues: CommandQueue[];
}

export interface ProcessorsWrapper {
  processor: Processor[];
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
  source: string[];
}

export interface IndexResult {
  group: IndexGroup[];
}

export interface PacketNameWrapper {
  name: string[];
}

export interface BucketsWrapper {
  buckets: Bucket[];
}

export interface RocksDbDatabasesWrapper {
  databases: RocksDbDatabase[];
}
