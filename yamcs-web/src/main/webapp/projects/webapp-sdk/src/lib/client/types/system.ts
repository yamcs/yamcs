import { WebSocketCall } from '../WebSocketCall';
import { Processor } from './processing';

export interface AuthInfo {
  requireAuthentication: boolean;
  spnego: boolean;
  openid?: OpenIDConnectInfo;
}

export interface OpenIDConnectInfo {
  clientId: string;
  authorizationEndpoint: string;
  scope: string;
}

export interface TokenResponse {
  access_token: string;
  token_type: string;
  expires_in: number;
  refresh_token: string;
  user: UserInfo;
}

export interface InstanceConfig {
  displayBucket: string;
  stackBucket: string;
  parameterArchive: boolean;
}

export interface GeneralInfo {
  yamcsVersion: string;
  revision: string;
  serverId: string;
  plugins: PluginInfo[];
  commandOptions: CommandOption[];
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

export interface ListThreadsResponse {
  threads: ThreadInfo[];
}

export interface ThreadGroup {
  name: string;
  parent?: ThreadGroup;
}

export interface ThreadInfo {
  id: number;
  name: string;
  state: string;
  native: boolean;
  suspended: boolean;
  group?: ThreadGroup;
  trace: TraceElementInfo[];
}

export interface TraceElementInfo {
  className: string;
  fileName: string;
  methodName: string;
  lineNumber: number;
}

export interface ListClearancesResponse {
  clearances: Clearance[];
}

export interface Database {
  name: string;
  tablespace: string;
  path: string;
  tables: string[];
  streams: string[];
}

export interface ListDatabasesResponse {
  databases: Database[];
}

export interface Route {
  service: string;
  method: string;
  description: string;
  inputType: string;
  outputType: string;
  deprecated: boolean;
  url: string;
  httpMethod: string;
  requestCount: number;
  logFormat: string;
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

export type CommandOptionType = 'BOOLEAN' | 'STRING' | 'NUMBER' | 'TIMESTAMP';

export interface CommandOption {
  id: string;
  verboseName: string;
  type: CommandOptionType;
  help: string;
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
  missionTime: string;
  capabilities: string[];
  template?: string;
  templateArgs?: { [key: string]: string; };
  templateAvailable: boolean;
  templateChanged: boolean;
}

export interface InstanceTemplate {
  name: string;
  description: string;
  variables: TemplateVariable[];
}

export interface TemplateVariable {
  name: string;
  label?: string;
  help?: string;
  required: boolean;
  type: string;
  initial?: string;
  choices?: string[];
}

export interface ConnectionInfo {
  instance: Instance;
  processor?: Processor;
}

export interface HttpTraffic {
  readBytes: number;
  writtenBytes: number;
  readThroughput: number;
  writeThroughput: number;
  connections: ClientConnectionInfo[];
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

export interface AuditRecordsPage {
  records: AuditRecord[];
}

export interface GetAuditRecordsOptions {
  start?: string;
  stop?: string;
  q?: string;
  service?: string;
}

export interface AuditRecord {
  time: string;
  service: string;
  method: string;
  user: string;
  summary: string;
  request: any;
}

export interface SessionInfo {
  id: string;
  username: string;
  ipAddress: string;
  hostname: string;
  startTime: string;
  lastAccessTime: string;
  expirationTime: string;
}

export interface ResultSet {
  columns: any;
  rows: any;
}

export interface HttpRequestInfo {
  protocol: string;
  method: string;
  uri: string;
  keepAlive: string;
  userAgent: string;
}

export interface EditClearanceRequest {
  level: string;
}

export interface CreateUserRequest {
  name: string;
  displayName?: string;
  email?: string;
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
  groups?: GroupInfo[];
  roles?: RoleInfo[];
  identities?: ExternalIdentity[];
  clearance: string;

  systemPrivileges?: string[];
  objectPrivileges?: ObjectPrivilege[];
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
  users?: UserInfo[];
  serviceAccounts?: ServiceAccount[];
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
  objects: string[];
}

export interface RoleInfo {
  name: string;
  description: string;
  default: boolean;
  systemPrivileges?: string[];
  objectPrivileges?: ObjectPrivilege[];
}

export interface Clearance {
  username: string;
  level: string;
  issuedBy: string;
  issueTime: string;
  hasCommandPrivileges: boolean;
}

export type ClearanceSubscription = WebSocketCall<void, Clearance>;

export type ReplicationInfoSubscription = WebSocketCall<void, ReplicationInfo>;

export type SystemInfoSubscription = WebSocketCall<void, SystemInfo>;

export type HttpTrafficSubscription = WebSocketCall<void, HttpTraffic>;

export interface Service {
  instance: string;
  name: string;
  state: ServiceState;
  className: string;
}

export interface Bucket {
  name: string;
  size: number;
  maxSize: number;
  numObjects: number;
  maxObjects: number;
  created: string;
  directory?: string;
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

export interface CompactRocksDbDatabaseRequest {
  cfname?: string;
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
  maxNonHeapMemory: number;
  jvmThreadCount: number;
  rootDirectories: RootDirectory[];
  process: ProcessInfo;
}

export interface ProcessInfo {
  pid: number;
  user: string;
  command: string;
  arguments?: string[];
  startTime: string;
  totalCpuDuration: string;
  children?: ProcessInfo[];
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

export interface ReplicationInfo {
  masters: ReplicationMaster[];
  slaves: ReplicationSlave[];
}

export interface ReplicationMaster {
  instance: string;
  streams: string[];
  localAddress: string;
  remoteAddress: string;
  push: boolean;
  pushTo?: string;
  localTx: number;
  nextTx: number;
}

export interface ReplicationSlave {
  instance: string;
  streams: string;
  localAddress: string;
  remoteAddress: string;
  push: boolean;
  pullFrom?: string;
  tx: number;
}
