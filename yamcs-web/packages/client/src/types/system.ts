import { Observable } from 'rxjs';
import { CommandQueueEntry, Value } from './monitoring';

export interface WebsiteConfig {
  auth: AuthInfo;
  displayScope: BucketScope;
  stackScope: BucketScope;
  tag: string;
}

export type BucketScope = 'GLOBAL' | 'INSTANCE';

export interface AuthInfo {
  requireAuthentication: boolean;
  flow: AuthFlow[];
}

export interface AuthFlow {
  type: 'PASSWORD' | 'REDIRECT' | 'SPNEGO';
}

export interface TokenResponse {
  access_token: string;
  token_type: string;
  expires_in: number;
  refresh_token: string;
  user: UserInfo;
}

export interface GeneralInfo {
  yamcsVersion: string;
  revision: string;
  serverId: string;
  defaultYamcsInstance: string;
  plugin: PluginInfo;
}

export interface PluginInfo {
  name: string;
  description: string;
  version: string;
  vendor: string;
}

export type ServiceState = 'NEW'
  | 'STARTING'
  | 'RUNNING'
  | 'STOPPING'
  | 'TERMINATED'
  | 'FAILED';

export type InstanceState = 'OFFLINE'
  | 'INITIALIZING'
  | 'INITIALIZED'
  | 'STARTING'
  | 'RUNNING'
  | 'STOPPING'
  | 'FAILED';

export interface Instance {
  name: string;
  state: InstanceState;
  processor: Processor[];
  labels?: { [key: string]: string };
}

export interface InstanceTemplate {
  name: string;
  variable: TemplateVariable[];
}

export interface TemplateVariable {
  name: string;
  description?: string;
  required: boolean;
}

export interface ConnectionInfo {
  clientId: number;
  instance: Instance;
  processor: Processor;
}

export interface ConnectionInfoSubscriptionResponse {
  connectionInfo: ConnectionInfo;
  connectionInfo$: Observable<ConnectionInfo>;
}

export interface InstanceSubscriptionResponse {
  instance$: Observable<Instance>;
}

export interface ClientInfo {
  id: number;
  instance: string;
  username: string;
  applicationName: string;
  address: string;
  processorName: string;
  state: 'CONNECTED' | 'DISCONNECTED';
  loginTime: string;
}

export interface EditClientRequest {
  instance?: string;
  processor?: string;
}

export interface ClientSubscriptionResponse {
  client$: Observable<ClientInfo>;
}

export interface UserInfo {
  login: string;
  clientInfo: ClientInfo[];
  superuser: boolean;

  systemPrivilege: string[];
  objectPrivilege: ObjectPrivilege[];
}

export interface ObjectPrivilege {
  type: string;
  object: string[];
}

export interface Service {
  instance: string;
  name: string;
  state: ServiceState;
  className: string;
}

export interface Link {
  instance: string;
  name: string;
  type: string;
  spec: string;
  stream: string;
  disabled: boolean;
  dataInCount: number;
  dataOutCount: number;
  status: string;
  detailedStatus: string;
  parentName?: string;
}

export interface Processor {
  instance: string;
  name: string;
  type: string;
  creator: string;
  hasAlarms: boolean;
  hasCommanding: boolean;
  state: ServiceState;
  persistent: boolean;
  time: string;
  replay: boolean;
  replayRequest?: ReplayRequest;
  service: Service[];
}

export interface ReplayRequest {
  utcStart: string;
  utcStop: string;
  speed: ReplaySpeed;
}

export interface ReplaySpeed {
  type: 'AFAP' | 'FIXED_DELAY' | 'REALTIME';
  param: number;
}

export interface ProcessorSubscriptionRequest {
  allProcessors?: boolean;
  allInstances?: boolean;
}

export interface ProcessorSubscriptionResponse {
  processor: Processor;
  processor$: Observable<Processor>;
}

export interface LinkEvent {
  type: string;
  linkInfo: Link;
}

export interface LinkSubscriptionResponse {
  linkEvent$: Observable<LinkEvent>;
}

export interface StreamSubscriptionResponse {
  streamData$: Observable<StreamData>;
}

export interface Stream {
  name: string;
  column: Column[];
}

export interface StreamData {
  stream: string;
  column: ColumnData[];
}

export interface Column {
  name: string;
  type: string;
  enumValue: SQLEnumValue[];
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

export interface Statistics {
  instance: string;
  yProcessorName: string;
  tmstats: TmStatistics[];
  lastUpdated: string;
}

export interface StatisticsSubscriptionResponse {
  statistics$: Observable<Statistics>;
}

export interface TmStatistics {
  packetName: string;
  qualifiedName: string;
  receivedPackets: number;
  lastReceived: string;
  lastPacketTime: string;
  subscribedParameterCount: number;
}

export interface CommandQueue {
  instance: string;
  processorName: string;
  name: string;
  state: 'BLOCKED' | 'DISABLED' | 'ENABLED';
  nbSentCommands: number;
  nbRejectCommands: number;
  stateExpirationTimeS: number;
  entry: CommandQueueEntry[];
}

export interface CommandQueueSubscriptionResponse {
  commandQueue$: Observable<CommandQueue>;
}

export interface ListInstancesOptions {
  filter?: string;
}

export interface EditLinkOptions {
  state?: 'enabled' | 'disabled';
  resetCounters?: boolean;
}

export interface EditInstanceOptions {
  state: 'stopped' | 'restarted' | 'running';
}

export interface EditCommandQueueOptions {
  state: 'enabled' | 'disabled' | 'blocked';
}

export interface CommandQueueEvent {
  type: 'COMMAND_ADDED' | 'COMMAND_REJECTED' | 'COMMAND_SENT';
  data: CommandQueueEntry;
}

export interface CommandQueueEventSubscriptionResponse {
  commandQueueEvent$: Observable<CommandQueueEvent>;
}

export interface EditCommandQueueEntryOptions {
  state: 'released' | 'rejected';
}

export interface Bucket {
  name: string;
  size: number;
  numObjects: number;
}

export interface ListObjectsResponse {
  prefix: string[];
  object: ObjectInfo[];
}

export interface ObjectInfo {
  name: string;
  created: string;
  size: number;
  metadata: { [key: string]: string };
}

export interface CreateBucketRequest {
  name: string;
}

export interface ListObjectsOptions {
  prefix?: string;
  delimiter?: string;
}

export interface CreateInstanceRequest {
  name: string;
  template: string;
  templateArgs?: { [key: string]: string };
  labels?: { [key: string]: string };
}
