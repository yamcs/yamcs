import { Observable } from 'rxjs';
import { CommandHistoryEntry, CommandId, CommandQueueEntry, Value } from './monitoring';

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
  plugins: PluginInfo[];
}

export interface ListRoutesResponse {
  routes: Route[];
}

export interface ListTopicsResponse {
  topics: Topic[];
}

export interface ListProcessorTypesResponse {
  types: string[];
}

export interface ListClearancesResponse {
  clearances: Clearance[];
}

export interface Route {
  service: string;
  method: string;
  inputType: string;
  outputType: string;
  deprecated: boolean;
  url: string;
  httpMethod: string;
  requestCount: number;
}

export interface Topic {
  topic: string;
  service: string;
  method: string;
  inputType: string;
  outputType: string;
  deprecated: boolean;
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
  processors: Processor[];
  labels?: { [key: string]: string; };
}

export interface InstanceTemplate {
  name: string;
  variables: TemplateVariable[];
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
  clearance?: string;
}

export interface ConnectionInfoSubscriptionResponse {
  connectionInfo: ConnectionInfo;
  connectionInfo$: Observable<ConnectionInfo>;
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

export interface ClientConnectionInfo {
  id: string;
  open: boolean;
  active: boolean;
  writable: boolean;
  remoteAddress: string;
  localAddress: string;
  readBytes: number;
  writtenBytes: number;
  readThroughput: number;
  writeThroughput: number;
  httpRequest: HttpRequestInfo;
}

export interface HttpRequestInfo {
  protocol: string;
  method: string;
  uri: string;
  keepAlive: string;
  userAgent: string;
}

export interface EditClientRequest {
  instance?: string;
  processor?: string;
}

export interface EditClearanceRequest {
  level: string;
}

export interface CreateUserRequest {
  name: string;
  displayName: string;
  email: string;
  password?: string;
}

export interface EditUserRequest {
  displayName?: string;
  email?: string;
  active?: boolean;
  superuser?: boolean;
  password?: string;
  roleAssignment?: RoleAssignmentInfo;
}

export interface UserInfo {
  name: string;
  displayName: string;
  email: string;
  active: boolean;
  superuser: boolean;
  createdBy: UserInfo;
  creationTime: string;
  confirmationTime: string;
  lastLoginTime: string;
  groups: GroupInfo[];
  roles: RoleInfo[];
  clientInfo: ClientInfo[];
  identities: ExternalIdentity[];
  clearance: string;

  systemPrivilege: string[];
  objectPrivilege: ObjectPrivilege[];
}

export interface ExternalIdentity {
  identity: string;
  provider: string;
}

export interface ListServiceAccountsResponse {
  serviceAccounts: ServiceAccount[];
}

export interface CreateServiceAccountRequest {
  name: string;
}

export interface CreateServiceAccountResponse {
  name: string;
  applicationId: string;
  applicationSecret: string;
}

export interface ServiceAccount {
  name: string;
  active: boolean;
}

export interface GroupInfo {
  name: string;
  description: string;
  users: UserInfo[];
  serviceAccounts: ServiceAccount[];
}

export interface GroupMemberInfo {
  users?: string[];
  serviceAccounts?: string[];
}

export interface EditGroupRequest {
  newName?: string;
  description?: string;
  memberInfo?: GroupMemberInfo;
}

export interface RoleAssignmentInfo {
  roles?: string[];
}

export interface ObjectPrivilege {
  type: string;
  object: string[];
}

export interface RoleInfo {
  name: string;
  description: string;
  systemPrivileges: string[];
  objectPrivileges: ObjectPrivilege[];
}

export interface Clearance {
  username: string;
  level: string;
  issuedBy: string;
  issueTime: string;
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
  status: LinkStatus;
  detailedStatus: string;
  parentName?: string;
}

export type LinkStatus = 'OK' | 'UNAVAIL' | 'DISABLED' | 'FAILED';

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
  services: Service[];
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

export interface AlarmSubscriptionRequest {
  detail?: boolean;
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

export interface StreamEvent {
  type: string;
  name: string;
  dataCount: number;
}

export interface StreamEventSubscriptionResponse {
  streamEvent$: Observable<StreamEvent>;
}

export interface StreamSubscriptionResponse {
  streamData$: Observable<StreamData>;
}

export interface Stream {
  name: string;
  column: Column[];
  script: string;
  dataCount: number;
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
  processor: string;
  tmstats: TmStatistics[];
  lastUpdated: string;
}

export interface StatisticsSubscriptionResponse {
  statistics$: Observable<Statistics>;
}

export interface TmStatistics {
  packetName: string;
  receivedPackets: number;
  packetRate: number;
  dataRate: number;
  lastReceived: string;
  lastPacketTime: string;
  subscribedParameterCount: number;
}

export interface CommandQueue {
  instance: string;
  processorName: string;
  name: string;
  state: 'BLOCKED' | 'DISABLED' | 'ENABLED';
  users: string[];
  groups: string[];
  minLevel: string;
  nbSentCommands: number;
  nbRejectCommands: number;
  stateExpirationTimeS: number;
  entry: CommandQueueEntry[];
  order: number;
}

export interface CommandSubscriptionRequest {
  commandId?: CommandId[];
  ignorePastCommands?: boolean;
}

export interface CommandSubscriptionResponse {
  command$: Observable<CommandHistoryEntry>;
}

export interface CommandQueueSubscriptionResponse {
  commandQueue$: Observable<CommandQueue>;
}

export interface EditLinkOptions {
  state?: 'enabled' | 'disabled';
  resetCounters?: boolean;
}

export interface EditCommandQueueOptions {
  state: 'enabled' | 'disabled' | 'blocked';
}

export interface CommandQueueEvent {
  type: 'COMMAND_ADDED' | 'COMMAND_UPDATED' | 'COMMAND_REJECTED' | 'COMMAND_SENT';
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
  prefixes: string[];
  objects: ObjectInfo[];
}

export interface ObjectInfo {
  name: string;
  created: string;
  size: number;
  metadata: { [key: string]: string; };
}

export interface CreateBucketRequest {
  name: string;
}

export interface ListObjectsOptions {
  prefix?: string;
  delimiter?: string;
}

export interface CreateGroupRequest {
  name: string;
  description?: string;
  users?: string[];
  serviceAccounts?: string[];
}

export interface RocksDbDatabase {
  tablespace: string;
  dbPath: string;
  dataDir: string;
}

export interface SystemInfo {
  yamcsVersion: string;
  revision: string;
  serverId: string;
  uptime: number;
  jvm: string;
  workingDirectory: string;
  configDirectory: string;
  dataDirectory: string;
  cacheDirectory: string;
  os: string;
  arch: string;
  availableProcessors: number;
  loadAverage: number;
  heapMemory: number;
  usedHeapMemory: number;
  maxHeapMemory: number;
  nonHeapMemory: number;
  usedNonHeapMemory: number;
  usedMaxHeapMemory: number;
  jvmThreadCount: number;
  rootDirectories: RootDirectory[];
}

export interface RootDirectory {
  directory: string;
  type: string;
  totalSpace: number;
  unallocatedSpace: number;
  usableSpace: number;
}

export interface LeapSecondsTable {
  ranges: ValidityRange[];
}

export interface ValidityRange {
  start: string;
  stop: string;
  leapSeconds: number;
  taiDifference: number;
}
