import { BehaviorSubject } from 'rxjs';
import { FrameLossListener } from './FrameLossListener';
import { HttpError } from './HttpError';
import { HttpHandler } from './HttpHandler';
import { HttpInterceptor } from './HttpInterceptor';
import { SessionListener } from './SessionListener';
import { WebSocketClient } from './WebSocketClient';
import { ActivitiesPage, Activity, ActivityLog, ActivityLogSubscription, ActivityScriptsPage, ActivitySubscription, CompleteManualActivityOptions, ExecutorsWrapper, GetActivitiesOptions, GetActivityLogResponse, GlobalActivityStatus, GlobalActivityStatusSubscription, StartActivityOptions, SubscribeActivitiesRequest, SubscribeActivityLogRequest, SubscribeGlobalActivityStatusRequest } from './types/activities';
import { AcknowledgeAlarmOptions, Alarm, AlarmSubscription, ClearAlarmOptions, GetAlarmsOptions, GlobalAlarmStatus, GlobalAlarmStatusSubscription, ShelveAlarmOptions, SubscribeAlarmsRequest, SubscribeGlobalAlarmStatusRequest } from './types/alarms';
import { CommandSubscription, SubscribeCommandsRequest } from './types/commandHistory';
import { Cop1Config, Cop1Status, Cop1Subscription, DisableCop1Request, InitiateCop1Request, SubscribeCop1Request } from './types/cop1';
import { CreateEventRequest, DownloadEventsOptions, Event, EventSubscription, GetEventsOptions, SubscribeEventsRequest } from './types/events';
import { CreateTransferRequest, GetFileTransfersOptions, ListFilesRequest, ListFilesResponse, RemoteFileListSubscription, RunFileActionRequest, ServicesPage, SubscribeRemoteFileListRequest, SubscribeTransfersRequest, Transfer, TransferSubscription, TransfersPage } from './types/filetransfer';
import { AlarmsWrapper, CommandQueuesWrapper, EventsWrapper, GroupsWrapper, IndexResult, InstanceTemplatesWrapper, InstancesWrapper, LinksWrapper, ProcessorsWrapper, RangesWrapper, RecordsWrapper, RocksDbDatabasesWrapper, RolesWrapper, SamplesWrapper, ServicesWrapper, SessionsWrapper, SourcesWrapper, StreamsWrapper, TablesWrapper, UsersWrapper } from './types/internal';
import { CreateInstanceRequest, InstancesSubscription, Link, LinkEvent, LinkSubscription, ListInstancesOptions, SubscribeLinksRequest } from './types/management';
import { Algorithm, AlgorithmOverrides, AlgorithmStatus, AlgorithmTrace, AlgorithmsPage, Command, CommandsPage, Container, ContainersPage, GetAlgorithmsOptions, GetCommandsOptions, GetContainersOptions, GetParameterTypesOptions, GetParametersOptions, MissionDatabase, NamedObjectId, Parameter, ParameterType, ParameterTypesPage, ParametersPage, SpaceSystem, SpaceSystemsPage } from './types/mdb';
import { ArchiveRecord, CommandHistoryEntry, CommandHistoryPage, CreateProcessorRequest, DownloadPacketsOptions, DownloadParameterValuesOptions, EditReplayProcessorRequest, ExecutorInfo, ExportParameterValuesOptions, GetCommandHistoryOptions, GetCompletenessIndexOptions, GetPacketsOptions, GetParameterRangesOptions, GetParameterSamplesOptions, GetParameterValuesOptions, IndexGroup, IssueCommandOptions, IssueCommandResponse, ListPacketsResponse, Packet, ParameterData, ParameterValue, Range, Sample, StartProcedureOptions, StreamCommandIndexOptions, StreamCompletenessIndexOptions, StreamEventIndexOptions, StreamPacketIndexOptions, StreamParameterIndexOptions, Value } from './types/monitoring';
import { CreateParameterListRequest, GetParameterListsResponse, ParameterList, UpdateParameterListRequest } from './types/plists';
import { AlgorithmStatusSubscription, BackfillingSubscription, DownloadCommandsOptions, ExtractPacketResponse, PacketNamesResponse, ParameterSubscription, Processor, ProcessorSubscription, Statistics, SubscribeAlgorithmStatusRequest, SubscribeBackfillingData, SubscribeBackfillingRequest, SubscribeParametersData, SubscribeParametersRequest, SubscribeProcessorsRequest, SubscribeTMStatisticsRequest, TMStatisticsSubscription } from './types/processing';
import { CommandQueue, CommandQueueEvent, QueueEventsSubscription, QueueStatisticsSubscription, SubscribeQueueEventsRequest, SubscribeQueueStatisticsRequest } from './types/queue';
import { SessionEvent, SessionSubscription } from './types/session';
import { AuditRecordsPage, AuthInfo, Clearance, ClearanceSubscription, CompactRocksDbDatabaseRequest, CreateGroupRequest, CreateServiceAccountRequest, CreateServiceAccountResponse, CreateUserRequest, Database, EditClearanceRequest, EditGroupRequest, EditUserRequest, GeneralInfo, GetAuditRecordsOptions, GroupInfo, HttpTraffic, HttpTrafficSubscription, Instance, InstanceConfig, InstanceTemplate, LeapSecondsTable, ListClearancesResponse, ListDatabasesResponse, ListProcessorTypesResponse, ListRoutesResponse, ListServiceAccountsResponse, ListThreadsResponse, ListTopicsResponse, ReplicationInfo, ReplicationInfoSubscription, ResultSet, RoleInfo, Service, ServiceAccount, SystemInfo, SystemInfoSubscription, ThreadInfo, TokenResponse, UserInfo } from './types/system';
import { Record, Stream, StreamData, StreamEvent, StreamStatisticsSubscription, StreamSubscription, SubscribeStreamRequest, SubscribeStreamStatisticsRequest, Table } from './types/table';
import { SubscribeTimeRequest, Time, TimeSubscription } from './types/time';
import { CreateTimelineBandRequest, CreateTimelineItemRequest, CreateTimelineViewRequest, GetTimelineItemsOptions, TimelineBand, TimelineBandsPage, TimelineItem, TimelineItemsPage, TimelineTagsPage, TimelineView, TimelineViewsPage, UpdateTimelineBandRequest, UpdateTimelineItemRequest, UpdateTimelineViewRequest } from './types/timeline';
import { CreateQueryRequest, EditQueryRequest, ListQueriesResponse, ParseFilterData, ParseFilterRequest, ParseFilterSubscription, Query } from './types/web';


export default class YamcsClient implements HttpHandler {

  readonly apiUrl: string;
  readonly authUrl: string;

  private accessToken?: string;

  private interceptor: HttpInterceptor;

  readonly connected$ = new BehaviorSubject<boolean>(false);
  private webSocketClient?: WebSocketClient;

  constructor(
    readonly baseHref = '/',
    private frameLossListener: FrameLossListener,
    private sessionListener: SessionListener,
  ) {
    this.apiUrl = `${this.baseHref}api`;
    this.authUrl = `${this.baseHref}auth`;
  }

  createInstancesSubscription(observer: (instance: Instance) => void): InstancesSubscription {
    return this.webSocketClient!.createSubscription('instances', {}, observer);
  }

  /**
   * Returns general auth information. This request
   * does not itself require authenticated access.
   */
  async getAuthInfo() {
    const response = await this.doFetch(`${this.authUrl}`);
    return await response.json() as AuthInfo;
  }

  /**
   * Log in to the Yamcs API.
   * This will return a short-lived access token and an indeterminate refresh token.
   */
  async fetchAccessTokenWithPassword(username: string, password: string) {
    let body = 'grant_type=password';
    body += `&username=${encodeURIComponent(username)}`;
    body += `&password=${encodeURIComponent(password)}`;
    return this.doFetchAccessToken(body);
  }

  async fetchAccessTokenWithAuthorizationCode(authorizationCode: string) {
    let body = 'grant_type=authorization_code';
    body += `&code=${encodeURIComponent(authorizationCode)}`;
    return this.doFetchAccessToken(body);
  }

  async fetchAccessTokenWithRefreshToken(refreshToken: string) {
    let body = 'grant_type=refresh_token';
    body += `&refresh_token=${encodeURIComponent(refreshToken)}`;
    return this.doFetchAccessToken(body);
  }

  /**
   * Set or update the access token for use by this client. Access tokens are short-lived, so you
   * probably have to call this method regularly by first using your refresh token to request
   * a new access token. This client does not automatically refresh for you, as it does not keep
   * track of any issued refresh tokens.
   *
   * In order to handle common token problems, consider adding an In and/or Out Interceptor.
   *
   * - An In Interceptor can prevent requests with expired access tokens. If you still have
   *   access to a refresh token, you can fetch and install a new access token before continuing
   *   the request. Else you may need to ask the user to re-login.
   *
   * - An Out Interceptor can respond to any 401 issues that may still occur. For example,
   *   because an access token was used that is not or no longer accepted by
   *   the server. If you still have access to a refresh token, you can fetch and install
   *   a new access token before re-issuing the request. Else you may need to ask the user
   *   to re-login.
   */
  public setAccessToken(accessToken: string) {
    this.accessToken = accessToken;
  }

  public clearAccessToken() {
    this.accessToken = undefined;
  }

  private async doFetchAccessToken(body: string) {
    const headers = new Headers();
    headers.append('Content-Type', 'application/x-www-form-urlencoded');
    const response = await fetch(`${this.authUrl}/token`, {
      method: 'POST',
      headers,
      body,
    });

    if (response.status >= 200 && response.status < 300) {
      const tokenResponse = await response.json() as TokenResponse;
      return Promise.resolve(tokenResponse);
    } else {
      return Promise.reject(new HttpError(response));
    }
  }

  /**
   * Register an interceptor that will have the opportunity
   * to inspect, modify, halt, or respond to any request.
   */
  setHttpInterceptor(interceptor: HttpInterceptor) {
    this.interceptor = interceptor;
  }

  async getGeneralInfo() {
    const response = await this.doFetch(this.apiUrl);
    return await response.json() as GeneralInfo;
  }

  /**
   * Returns info on the authenticated user
   */
  async getUserInfo() {
    const response = await this.doFetch(`${this.apiUrl}/user`);
    return await response.json() as UserInfo;
  }

  async getRoutes() {
    const url = `${this.apiUrl}/routes`;
    const response = await this.doFetch(url);
    return await response.json() as ListRoutesResponse;
  }

  async getTopics() {
    const url = `${this.apiUrl}/topics`;
    const response = await this.doFetch(url);
    return await response.json() as ListTopicsResponse;
  }

  async getProcessorTypes() {
    const url = `${this.apiUrl}/processor-types`;
    const response = await this.doFetch(url);
    return await response.json() as ListProcessorTypesResponse;
  }

  async getThreads() {
    const url = `${this.apiUrl}/threads`;
    const response = await this.doFetch(url);
    return await response.json() as ListThreadsResponse;
  }

  async getThread(id: number) {
    const url = `${this.apiUrl}/threads/${id}`;
    const response = await this.doFetch(url);
    return await response.json() as ThreadInfo;
  }

  async getReplicationInfo() {
    const url = `${this.apiUrl}/replication`;
    const response = await this.doFetch(url);
    return await response.json() as ReplicationInfo;
  }

  async getClearances() {
    const url = `${this.apiUrl}/clearances`;
    const response = await this.doFetch(url);
    return await response.json() as ListClearancesResponse;
  }

  async changeClearance(username: string, options: EditClearanceRequest) {
    const body = JSON.stringify(options);
    return this.doFetch(`${this.apiUrl}/clearances/${username}`, {
      method: 'PATCH',
      body,
    });
  }

  async deleteClearance(username: string) {
    return this.doFetch(`${this.apiUrl}/clearances/${username}`, {
      method: 'DELETE',
    });
  }

  async createProcessor(options: CreateProcessorRequest) {
    const body = JSON.stringify(options);
    return await this.doFetch(`${this.apiUrl}/processors`, {
      body,
      method: 'POST',
    });
  }

  async getLeapSeconds() {
    const url = `${this.apiUrl}/leap-seconds`;
    const response = await this.doFetch(url);
    return await response.json() as LeapSecondsTable;
  }

  async getInstances(options: ListInstancesOptions = {}) {
    const url = `${this.apiUrl}/instances`;
    const response = await this.doFetch(url + this.queryString(options));
    const wrapper = await response.json() as InstancesWrapper;
    return wrapper.instances || [];
  }

  async getInstanceTemplates() {
    const response = await this.doFetch(`${this.apiUrl}/instance-templates`);
    const wrapper = await response.json() as InstanceTemplatesWrapper;
    return wrapper.templates || [];
  }

  async getInstanceTemplate(name: string) {
    const response = await this.doFetch(`${this.apiUrl}/instance-templates/${name}`);
    return await response.json() as InstanceTemplate;
  }

  async getInstance(name: string) {
    const response = await this.doFetch(`${this.apiUrl}/instances/${name}`);
    return await response.json() as Instance;
  }

  async getInstanceConfig(instance: string) {
    const response = await this.doFetch(`${this.apiUrl}/web/instance-config/${instance}`);
    return await response.json() as InstanceConfig;
  }

  async startInstance(name: string) {
    return this.doFetch(`${this.apiUrl}/instances/${name}:start`, {
      method: 'POST',
    });
  }

  async stopInstance(name: string) {
    return this.doFetch(`${this.apiUrl}/instances/${name}:stop`, {
      method: 'POST',
    });
  }

  async restartInstance(name: string) {
    return this.doFetch(`${this.apiUrl}/instances/${name}:restart`, {
      method: 'POST',
    });
  }

  async getDatabases(): Promise<Database[]> {
    const url = `${this.apiUrl}/databases`;
    const response = await this.doFetch(url);
    const wrapper = await response.json() as ListDatabasesResponse;
    return wrapper.databases || [];
  }

  async getDatabase(name: string) {
    const url = `${this.apiUrl}/databases/${name}`;
    const response = await this.doFetch(url);
    return await response.json() as Database;
  }

  async getServices(instance: string): Promise<Service[]> {
    const url = `${this.apiUrl}/services/${instance}`;
    const response = await this.doFetch(url);
    const wrapper = await response.json() as ServicesWrapper;
    return wrapper.services || [];
  }

  async getService(instance: string, name: string): Promise<Service> {
    const url = `${this.apiUrl}/services/${instance}/${name}`;
    const response = await this.doFetch(url);
    return await response.json() as Service;
  }

  async startService(instance: string, name: string) {
    return this.doFetch(`${this.apiUrl}/services/${instance}/${name}:start`, {
      method: 'POST',
    });
  }

  async stopService(instance: string, name: string) {
    return this.doFetch(`${this.apiUrl}/services/${instance}/${name}:stop`, {
      method: 'POST',
    });
  }

  async getServiceAccounts() {
    const url = `${this.apiUrl}/service-accounts`;
    const response = await this.doFetch(url);
    return await response.json() as ListServiceAccountsResponse;
  }

  async getServiceAccount(name: string) {
    const url = `${this.apiUrl}/service-accounts/${name}`;
    const response = await this.doFetch(url);
    return await response.json() as ServiceAccount;
  }

  async createServiceAccount(options: CreateServiceAccountRequest) {
    const body = JSON.stringify(options);
    const url = `${this.apiUrl}/service-accounts`;
    const response = await this.doFetch(url, {
      body,
      method: 'POST',
    });
    return await response.json() as CreateServiceAccountResponse;
  }

  async deleteServiceAccount(name: string) {
    const url = `${this.apiUrl}/service-accounts/${name}`;
    return await this.doFetch(url, {
      method: 'DELETE',
    });
  }

  async getTimelineViews(instance: string) {
    const url = `${this.apiUrl}/timeline/${instance}/views`;
    const response = await this.doFetch(url);
    return await response.json() as TimelineViewsPage;
  }

  async getTimelineView(instance: string, id: string) {
    const url = `${this.apiUrl}/timeline/${instance}/views/${id}`;
    const response = await this.doFetch(url);
    return await response.json() as TimelineView;
  }

  async createTimelineView(instance: string, options: CreateTimelineViewRequest) {
    const body = JSON.stringify(options);
    const url = `${this.apiUrl}/timeline/${instance}/views`;
    const response = await this.doFetch(url, {
      body,
      method: 'POST',
    });
    return await response.json() as TimelineView;
  }

  async updateTimelineView(instance: string, id: string, options: UpdateTimelineViewRequest) {
    const body = JSON.stringify(options);
    const url = `${this.apiUrl}/timeline/${instance}/views/${id}`;
    const response = await this.doFetch(url, {
      body,
      method: 'PUT',
    });
    return await response.json() as TimelineView;
  }

  async deleteTimelineView(instance: string, id: string) {
    const url = `${this.apiUrl}/timeline/${instance}/views/${id}`;
    return await this.doFetch(url, {
      method: 'DELETE'
    });
  }

  async getAuditRecords(instance: string, options: GetAuditRecordsOptions) {
    const url = `${this.apiUrl}/audit/records/${instance}`;
    const response = await this.doFetch(url + this.queryString(options));
    return await response.json() as AuditRecordsPage;
  }

  async getTimelineTags(instance: string) {
    const url = `${this.apiUrl}/timeline/${instance}/tags`;
    const response = await this.doFetch(url);
    return await response.json() as TimelineTagsPage;
  }

  async getTimelineBands(instance: string) {
    const url = `${this.apiUrl}/timeline/${instance}/bands`;
    const response = await this.doFetch(url);
    return await response.json() as TimelineBandsPage;
  }

  async getTimelineBand(instance: string, id: string) {
    const url = `${this.apiUrl}/timeline/${instance}/bands/${id}`;
    const response = await this.doFetch(url);
    return await response.json() as TimelineBand;
  }

  async getTimelineItems(instance: string, options: GetTimelineItemsOptions) {
    const url = `${this.apiUrl}/timeline/${instance}/items`;
    const response = await this.doFetch(url + this.queryString(options));
    return await response.json() as TimelineItemsPage;
  }

  async getTimelineItem(instance: string, id: string) {
    const url = `${this.apiUrl}/timeline/${instance}/items/${id}`;
    const response = await this.doFetch(url);
    return await response.json() as TimelineItem;
  }

  async createTimelineBand(instance: string, options: CreateTimelineBandRequest) {
    const body = JSON.stringify(options);
    const url = `${this.apiUrl}/timeline/${instance}/bands`;
    const response = await this.doFetch(url, {
      body,
      method: 'POST',
    });
    return await response.json() as TimelineBand;
  }

  async updateTimelineBand(instance: string, id: string, options: UpdateTimelineBandRequest) {
    const body = JSON.stringify(options);
    const url = `${this.apiUrl}/timeline/${instance}/bands/${id}`;
    const response = await this.doFetch(url, {
      body,
      method: 'PUT',
    });
    return await response.json() as TimelineBand;
  }

  async deleteTimelineBand(instance: string, id: string) {
    const url = `${this.apiUrl}/timeline/${instance}/bands/${id}`;
    return await this.doFetch(url, {
      method: 'DELETE'
    });
  }

  async createTimelineItem(instance: string, options: CreateTimelineItemRequest) {
    const body = JSON.stringify(options);
    const url = `${this.apiUrl}/timeline/${instance}/items`;
    const response = await this.doFetch(url, {
      body,
      method: 'POST',
    });
    return await response.json() as TimelineItem;
  }

  async updateTimelineItem(instance: string, id: string, options: UpdateTimelineItemRequest) {
    const body = JSON.stringify(options);
    const url = `${this.apiUrl}/timeline/${instance}/items/${id}`;
    const response = await this.doFetch(url, {
      body,
      method: 'PUT',
    });
    return await response.json() as TimelineItem;
  }

  async deleteTimelineItem(instance: string, id: string) {
    const url = `${this.apiUrl}/timeline/${instance}/items/${id}`;
    return await this.doFetch(url, {
      method: 'DELETE',
    });
  }

  async getParameterLists(instance: string) {
    const url = `${this.apiUrl}/parameter-lists/${instance}/lists`;
    const response = await this.doFetch(url);
    const wrapper = await response.json() as GetParameterListsResponse;
    return wrapper.lists || [];
  }

  async getParameterList(instance: string, id: string) {
    const url = `${this.apiUrl}/parameter-lists/${instance}/lists/${id}`;
    const response = await this.doFetch(url);
    return await response.json() as ParameterList;
  }

  async createParameterList(instance: string, options: CreateParameterListRequest) {
    const body = JSON.stringify(options);
    const response = await this.doFetch(`${this.apiUrl}/parameter-lists/${instance}/lists`, {
      body,
      method: 'POST',
    });
    return await response.json() as ParameterList;
  }

  async updateParameterList(instance: string, id: string, options: UpdateParameterListRequest) {
    const body = JSON.stringify(options);
    const response = await this.doFetch(`${this.apiUrl}/parameter-lists/${instance}/lists/${id}`, {
      body,
      method: 'PATCH',
    });
    return await response.json() as ParameterList;
  }

  async deleteParameterList(instance: string, id: string) {
    const url = `${this.apiUrl}/parameter-lists/${instance}/lists/${id}`;
    return await this.doFetch(url, { method: 'DELETE' });
  }

  async getUsers() {
    const url = `${this.apiUrl}/users`;
    const response = await this.doFetch(url);
    const wrapper = await response.json() as UsersWrapper;
    return wrapper.users || [];
  }

  async getUser(username: string) {
    const url = `${this.apiUrl}/users/${username}`;
    const response = await this.doFetch(url);
    return await response.json() as UserInfo;
  }

  async createUser(options: CreateUserRequest) {
    const body = JSON.stringify(options);
    const url = `${this.apiUrl}/users`;
    return await this.doFetch(url, {
      body,
      method: 'POST',
    });
  }

  async editUser(username: string, options: EditUserRequest) {
    const body = JSON.stringify(options);
    const url = `${this.apiUrl}/users/${username}`;
    return await this.doFetch(url, {
      body,
      method: 'PATCH',
    });
  }

  async deleteUser(name: string) {
    const url = `${this.apiUrl}/users/${name}`;
    return await this.doFetch(url, {
      method: 'DELETE',
    });
  }

  async deleteIdentity(username: string, provider: string) {
    const url = `${this.apiUrl}/users/${username}/identities/${provider}`;
    return await this.doFetch(url, { method: 'DELETE' });
  }

  async createGroup(options: CreateGroupRequest) {
    const body = JSON.stringify(options);
    const response = await this.doFetch(`${this.apiUrl}/groups`, {
      body,
      method: 'POST',
    });
    return await response.json() as GroupInfo;
  }

  async getGroups() {
    const url = `${this.apiUrl}/groups`;
    const response = await this.doFetch(url);
    const wrapper = await response.json() as GroupsWrapper;
    return wrapper.groups || [];
  }

  async getGroup(name: string) {
    const url = `${this.apiUrl}/groups/${name}`;
    const response = await this.doFetch(url);
    return await response.json() as GroupInfo;
  }

  async editGroup(name: string, options: EditGroupRequest) {
    const body = JSON.stringify(options);
    const url = `${this.apiUrl}/groups/${name}`;
    return await this.doFetch(url, {
      body,
      method: 'PATCH',
    });
  }

  async deleteGroup(name: string) {
    const url = `${this.apiUrl}/groups/${name}`;
    return await this.doFetch(url, { method: 'DELETE' });
  }

  async getRoles() {
    const url = `${this.apiUrl}/roles`;
    const response = await this.doFetch(url);
    const wrapper = await response.json() as RolesWrapper;
    return wrapper.roles || [];
  }

  async getRole(name: string) {
    const url = `${this.apiUrl}/roles/${name}`;
    const response = await this.doFetch(url);
    return await response.json() as RoleInfo;
  }

  async getSessions() {
    const url = `${this.apiUrl}/sessions`;
    const response = await this.doFetch(url);
    const wrapper = await response.json() as SessionsWrapper;
    return wrapper.sessions || [];
  }

  async getHttpTraffic() {
    const url = `${this.apiUrl}/http-traffic`;
    const response = await this.doFetch(url);
    return await response.json() as HttpTraffic;
  }

  async closeClientConnection(id: string) {
    const url = `${this.apiUrl}/connections/${id}`;
    return await this.doFetch(url, { method: 'DELETE' });
  }

  createSessionSubscription(observer: (sessionEvent: SessionEvent) => void): SessionSubscription {
    return this.webSocketClient!.createSubscription('session', {}, observer);
  }

  createTimeSubscription(options: SubscribeTimeRequest, observer: (time: Time) => void): TimeSubscription {
    return this.webSocketClient!.createSubscription('time', options, observer);
  }

  createReplicationInfoSubscription(observer: (info: ReplicationInfo) => void): ReplicationInfoSubscription {
    return this.webSocketClient!.createSubscription('replication-info', {}, observer);
  }

  createSystemInfoSubscription(observer: (info: SystemInfo) => void): SystemInfoSubscription {
    return this.webSocketClient!.createSubscription('sysinfo', {}, observer);
  }

  createHttpTrafficSubscription(observer: (info: HttpTraffic) => void): HttpTrafficSubscription {
    return this.webSocketClient!.createSubscription('http-traffic', {}, observer);
  }

  createClearanceSubscription(observer: (clearance: Clearance) => void): ClearanceSubscription {
    return this.webSocketClient!.createSubscription('clearance', {}, observer);
  }

  createProcessorSubscription(options: SubscribeProcessorsRequest, observer: (processor: Processor) => void): ProcessorSubscription {
    return this.webSocketClient!.createSubscription('processors', options, observer);
  }

  createBackfillingSubscription(options: SubscribeBackfillingRequest, observer: (data: SubscribeBackfillingData) => void): BackfillingSubscription {
    return this.webSocketClient!.createSubscription('backfilling', options, observer);
  }

  createCop1Subscription(options: SubscribeCop1Request, observer: (cop1Status: Cop1Status) => void): Cop1Subscription {
    return this.webSocketClient!.createSubscription('cop1', options, observer);
  }

  createGlobalAlarmStatusSubscription(options: SubscribeGlobalAlarmStatusRequest, observer: (status: GlobalAlarmStatus) => void): GlobalAlarmStatusSubscription {
    return this.webSocketClient!.createSubscription('global-alarm-status', options, observer);
  }

  createGlobalActivityStatusSubscription(options: SubscribeGlobalActivityStatusRequest, observer: (status: GlobalActivityStatus) => void): GlobalActivityStatusSubscription {
    return this.webSocketClient!.createSubscription('global-activity-status', options, observer);
  }

  createTMStatisticsSubscription(options: SubscribeTMStatisticsRequest, observer: (time: Statistics) => void): TMStatisticsSubscription {
    return this.webSocketClient!.createSubscription('tmstats', options, observer);
  }

  createAlgorithmStatusSubscription(options: SubscribeAlgorithmStatusRequest, observer: (status: AlgorithmStatus) => void): AlgorithmStatusSubscription {
    return this.webSocketClient!.createSubscription('algorithm-status', options, observer);
  }

  createEventSubscription(options: SubscribeEventsRequest, observer: (event: Event) => void): EventSubscription {
    return this.webSocketClient!.createSubscription('events', options, observer);
  }

  createLinkSubscription(options: SubscribeLinksRequest, observer: (linkEvent: LinkEvent) => void): LinkSubscription {
    return this.webSocketClient!.createSubscription('links', options, observer);
  }

  createActivitySubscription(options: SubscribeActivitiesRequest, observer: (update: Activity) => void): ActivitySubscription {
    return this.webSocketClient!.createSubscription('activities', options, observer);
  }

  createActivityLogSubscription(options: SubscribeActivityLogRequest, observer: (update: ActivityLog) => void): ActivityLogSubscription {
    return this.webSocketClient!.createSubscription('activity-log', options, observer);
  }

  createTransferSubscription(options: SubscribeTransfersRequest, observer: (transfer: Transfer) => void): TransferSubscription {
    return this.webSocketClient!.createSubscription('file-transfers', options, observer);
  }

  createRemoteFileListSubscription(options: SubscribeRemoteFileListRequest, observer: (fileList: ListFilesResponse) => void): RemoteFileListSubscription {
    return this.webSocketClient!.createSubscription('remote-file-list', options, observer);
  }

  createParameterSubscription(options: SubscribeParametersRequest, observer: (data: SubscribeParametersData) => void): ParameterSubscription {
    // Allow dropped frames in case of high rate
    return this.webSocketClient!.createLowPrioritySubscription('parameters', options, observer);
  }

  createParseFilterSubscription(options: ParseFilterRequest, observer: (data: ParseFilterData) => void): ParseFilterSubscription {
    return this.webSocketClient!.createSubscription('web.parse-filter', options, observer);
  }

  createStreamStatisticsSubscription(options: SubscribeStreamStatisticsRequest, observer: (event: StreamEvent) => void): StreamStatisticsSubscription {
    return this.webSocketClient!.createSubscription('stream-stats', options, observer);
  }

  createStreamSubscription(options: SubscribeStreamRequest, observer: (data: StreamData) => void): StreamSubscription {
    // Allow dropped frames in case of high rate
    return this.webSocketClient!.createLowPrioritySubscription('stream', options, observer);
  }

  createQueueStatisticsSubscription(options: SubscribeQueueStatisticsRequest, observer: (queue: CommandQueue) => void): QueueStatisticsSubscription {
    return this.webSocketClient!.createSubscription('queue-stats', options, observer);
  }

  createQueueEventsSubscription(options: SubscribeQueueEventsRequest, observer: (event: CommandQueueEvent) => void): QueueEventsSubscription {
    return this.webSocketClient!.createSubscription('queue-events', options, observer);
  }

  createAlarmSubscription(options: SubscribeAlarmsRequest, observer: (alarm: Alarm) => void): AlarmSubscription {
    return this.webSocketClient!.createSubscription('alarms', options, observer);
  }

  createCommandSubscription(options: SubscribeCommandsRequest, observer: (entry: CommandHistoryEntry) => void): CommandSubscription {
    return this.webSocketClient!.createSubscription('commands', options, observer);
  }

  async getSystemInfo() {
    const url = `${this.apiUrl}/sysinfo`;
    const response = await this.doFetch(url);
    return await response.json() as SystemInfo;
  }

  async getRocksDbDatabases() {
    const url = `${this.apiUrl}/archive/rocksdb/databases`;
    const response = await this.doFetch(url);
    const wrapper = await response.json() as RocksDbDatabasesWrapper;
    return wrapper.databases || [];
  }

  async getRocksDbDatabaseProperties(tablespace: string, dbPath = '') {
    const url = `${this.apiUrl}/archive/rocksdb/${tablespace}/${dbPath}:describe`;
    const response = await this.doFetch(url);
    return await response.text();
  }

  async compactRocksDbDatabase(tablespace: string, dbPath = '', options: CompactRocksDbDatabaseRequest) {
    const body = JSON.stringify(options);
    const url = `${this.apiUrl}/archive/rocksdb/${tablespace}/${dbPath}:compact`;
    return await this.doFetch(url, {
      body,
      method: 'POST',
    });
  }

  getThreadDumpURL() {
    return `${this.apiUrl}/threads:dump`;
  }

  async createInstance(options: CreateInstanceRequest) {
    const body = JSON.stringify(options);
    const response = await this.doFetch(`${this.apiUrl}/instances`, {
      body,
      method: 'POST',
    });
    return await response.json() as Instance;
  }

  async getPackets(instance: string, options: GetPacketsOptions = {}) {
    const url = `${this.apiUrl}/archive/${instance}/packets`;
    const response = await this.doFetch(url + this.queryString(options));
    return await response.json() as ListPacketsResponse;
  }

  async getEvents(instance: string, options: GetEventsOptions = {}) {
    const url = `${this.apiUrl}/archive/${instance}/events:list`;
    const body = JSON.stringify(options);
    const response = await this.doFetch(url, {
      body,
      method: 'POST',
    });
    const wrapper = await response.json() as EventsWrapper;
    return wrapper.events || [];
  }

  /**
   * Retrieves the distinct sources for the currently archived events.
   */
  async getEventSources(instance: string) {
    const url = `${this.apiUrl}/archive/${instance}/events/sources`;
    const response = await this.doFetch(url);
    const wrapper = await response.json() as SourcesWrapper;
    return wrapper.sources || [];
  }

  getEventsDownloadURL(instance: string, options: DownloadEventsOptions = {}) {
    const url = `${this.apiUrl}/archive/${instance}:exportEvents`;
    return url + this.queryString(options);
  }

  async createEvent(instance: string, options: CreateEventRequest) {
    const body = JSON.stringify(options);
    const response = await this.doFetch(`${this.apiUrl}/archive/${instance}/events`, {
      body,
      method: 'POST',
    });
    return await response.json() as Event;
  }

  async getQueries(instance: string, resource: string) {
    const url = `${this.apiUrl}/web/queries/${instance}/${resource}`;
    const response = await this.doFetch(url);
    const wrapper = await response.json() as ListQueriesResponse;
    return wrapper.queries || [];
  }

  async createQuery(instance: string, resource: string, options: CreateQueryRequest) {
    const body = JSON.stringify(options);
    const response = await this.doFetch(`${this.apiUrl}/web/queries/${instance}/${resource}`, {
      body,
      method: 'POST',
    });
    return await response.json() as Query;
  }

  async editQuery(instance: string, resource: string, queryId: string, options: EditQueryRequest) {
    const body = JSON.stringify(options);
    const url = `${this.apiUrl}/web/queries/${instance}/${resource}/${queryId}`;
    return await this.doFetch(url, {
      body,
      method: 'PATCH',
    });
  }

  async deleteQuery(instance: string, resource: string, queryId: string) {
    const url = `${this.apiUrl}/queries/${instance}/${resource}/${queryId}`;
    return await this.doFetch(url, {
      method: 'DELETE',
    });
  }

  getCommandsDownloadURL(instance: string, options: DownloadCommandsOptions = {}) {
    const url = `${this.apiUrl}/archive/${instance}:exportCommands`;
    return url + this.queryString(options);
  }

  async editReplayProcessor(instance: string, processor: string, options: EditReplayProcessorRequest) {
    const body = JSON.stringify(options);
    const url = `${this.apiUrl}/processors/${instance}/${processor}`;
    return await this.doFetch(url, {
      body,
      method: 'PATCH',
    });
  }

  async deleteReplayProcessor(instance: string, processor: string) {
    const url = `${this.apiUrl}/processors/${instance}/${processor}`;
    return await this.doFetch(url, {
      method: 'DELETE',
    });
  }

  async getLinks(instance: string): Promise<Link[]> {
    const url = `${this.apiUrl}/links/${instance}`;
    const response = await this.doFetch(url);
    const wrapper = await response.json() as LinksWrapper;
    return wrapper.links || [];
  }

  async getLink(instance: string, link: string): Promise<Link> {
    const url = `${this.apiUrl}/links/${instance}/${link}`;
    const response = await this.doFetch(url);
    return await response.json() as Link;
  }

  async enableLink(instance: string, link: string) {
    return this.doFetch(`${this.apiUrl}/links/${instance}/${link}:enable`, {
      method: 'POST',
    });
  }

  async disableLink(instance: string, link: string) {
    return this.doFetch(`${this.apiUrl}/links/${instance}/${link}:disable`, {
      method: 'POST',
    });
  }

  async runLinkAction(instance: string, link: string, action: string, message?: { [key: string]: any; }) {
    return this.doFetch(`${this.apiUrl}/links/${instance}/${link}/actions/${action}`, {
      method: 'POST',
      body: JSON.stringify(message || {}),
    });
  }

  async resetLinkCounters(instance: string, link: string) {
    return this.doFetch(`${this.apiUrl}/links/${instance}/${link}:resetCounters`, {
      method: 'POST',
    });
  }

  async getProcessors(instance: string) {
    const url = `${this.apiUrl}/processors/${instance}`;
    const response = await this.doFetch(url);
    const wrapper = await response.json() as ProcessorsWrapper;
    return wrapper.processors || [];
  }

  async getProcessor(instance: string, name: string) {
    const url = `${this.apiUrl}/processors/${instance}/${name}`;
    const response = await this.doFetch(url);
    return await response.json() as Processor;
  }

  async issueCommand(instance: string, processorName: string, qualifiedName: string, options?: IssueCommandOptions): Promise<IssueCommandResponse> {
    return this.issueCommandForNamespace(instance, processorName, null, qualifiedName, options);
  }

  async issueCommandForNamespace(instance: string, processorName: string, namespace: string | null, name: string, options?: IssueCommandOptions): Promise<IssueCommandResponse> {
    const fullName = namespace ? namespace + "/" + name : name;
    const encodedName = encodeURIComponent(fullName);
    const body = JSON.stringify(options);
    const response = await this.doFetch(`${this.apiUrl}/processors/${instance}/${processorName}/commands/${encodedName}`, {
      body,
      method: 'POST',
    });
    return await response.json() as IssueCommandResponse;
  }

  async startProcedure(instance: string, procedure: string, options?: StartProcedureOptions): Promise<ExecutorInfo> {
    const encodedName = encodeURIComponent(procedure);
    const body = JSON.stringify(options);
    const response = await this.doFetch(`${this.apiUrl}/spell/${instance}/procedures/${encodedName}:start`, {
      body,
      method: 'POST',
    });
    return await response.json();
  }

  async getCommandHistoryEntry(instance: string, id: string): Promise<CommandHistoryEntry> {
    const url = `${this.apiUrl}/archive/${instance}/commands/${id}`;
    const response = await this.doFetch(url);
    return await response.json() as CommandHistoryEntry;
  }

  getCommandDownloadURL(instance: string, id: string) {
    return `${this.apiUrl}/archive/${instance}/commands/${id}:export`;
  }

  async getCommandHistoryEntries(instance: string, options: GetCommandHistoryOptions = {}): Promise<CommandHistoryPage> {
    const url = `${this.apiUrl}/archive/${instance}/commands`;
    const response = await this.doFetch(url + this.queryString(options));
    return await response.json() as CommandHistoryPage;
  }

  async getCommandHistoryEntriesForParameter(instance: string, qualifiedName: string, options: GetCommandHistoryOptions = {}) {
    const url = `${this.apiUrl}/archive/${instance}/commands${qualifiedName}`;
    const response = await this.doFetch(url + this.queryString(options));
    return await response.json() as CommandHistoryPage;
  }

  async getCommandQueues(instance: string, processor: string) {
    const url = `${this.apiUrl}/processors/${instance}/${processor}/queues`;
    const response = await this.doFetch(url);
    const wrapper = await response.json() as CommandQueuesWrapper;
    return wrapper.queues || [];
  }

  async getCommandQueue(instance: string, processor: string, queue: string) {
    const url = `${this.apiUrl}/processors/${instance}/${processor}/queues/${queue}`;
    const response = await this.doFetch(url);
    return await response.json() as CommandQueue;
  }

  async enableCommandQueue(instance: string, processor: string, queue: string) {
    const url = `${this.apiUrl}/processors/${instance}/${processor}/queues/${queue}:enable`;
    const response = await this.doFetch(url, { method: 'POST' });
    return await response.json() as CommandQueue;
  }

  async disableCommandQueue(instance: string, processor: string, queue: string) {
    const url = `${this.apiUrl}/processors/${instance}/${processor}/queues/${queue}:disable`;
    const response = await this.doFetch(url, { method: 'POST' });
    return await response.json() as CommandQueue;
  }

  async blockCommandQueue(instance: string, processor: string, queue: string) {
    const url = `${this.apiUrl}/processors/${instance}/${processor}/queues/${queue}:block`;
    const response = await this.doFetch(url, { method: 'POST' });
    return await response.json() as CommandQueue;
  }

  async acceptCommand(instance: string, processor: string, queue: string, command: string) {
    const url = `${this.apiUrl}/processors/${instance}/${processor}/queues/${queue}/commands/${command}:accept`;
    await this.doFetch(url, { method: 'POST' });
  }

  async rejectCommand(instance: string, processor: string, queue: string, command: string) {
    const url = `${this.apiUrl}/processors/${instance}/${processor}/queues/${queue}/commands/${command}:reject`;
    await this.doFetch(url, { method: 'POST' });
  }

  async getActiveAlarms(instance: string, processor: string, options: GetAlarmsOptions = {}): Promise<Alarm[]> {
    const url = `${this.apiUrl}/processors/${instance}/${processor}/alarms`;
    const response = await this.doFetch(url + this.queryString(options));
    const wrapper = await response.json() as AlarmsWrapper;
    return wrapper.alarms || [];
  }

  async getAlarms(instance: string, options: GetAlarmsOptions = {}) {
    const url = `${this.apiUrl}/archive/${instance}/alarms`;
    const response = await this.doFetch(url + this.queryString(options));
    const wrapper = await response.json() as AlarmsWrapper;
    return wrapper.alarms || [];
  }

  async getAlarmsForParameter(instance: string, qualifiedName: string, options: GetAlarmsOptions = {}) {
    const url = `${this.apiUrl}/archive/${instance}/alarms${qualifiedName}`;
    const response = await this.doFetch(url + this.queryString(options));
    const wrapper = await response.json() as AlarmsWrapper;
    return await wrapper.alarms || [];
  }

  async acknowledgeAlarm(instance: string, processor: string, alarm: string, sequenceNumber: number, options: AcknowledgeAlarmOptions) {
    const body = JSON.stringify(options);
    const url = `${this.apiUrl}/processors/${instance}/${processor}/alarms${alarm}/${sequenceNumber}:acknowledge`;
    return await this.doFetch(url, {
      body,
      method: 'POST',
    });
  }

  async shelveAlarm(instance: string, processor: string, alarm: string, sequenceNumber: number, options: ShelveAlarmOptions) {
    const body = JSON.stringify(options);
    const url = `${this.apiUrl}/processors/${instance}/${processor}/alarms${alarm}/${sequenceNumber}:shelve`;
    return await this.doFetch(url, {
      body,
      method: 'POST',
    });
  }

  async unshelveAlarm(instance: string, processor: string, alarm: string, sequenceNumber: number) {
    const url = `${this.apiUrl}/processors/${instance}/${processor}/alarms${alarm}/${sequenceNumber}:unshelve`;
    return await this.doFetch(url, {
      method: 'POST',
    });
  }

  async clearAlarm(instance: string, processor: string, alarm: string, sequenceNumber: number, options: ClearAlarmOptions) {
    const body = JSON.stringify(options);
    const url = `${this.apiUrl}/processors/${instance}/${processor}/alarms${alarm}/${sequenceNumber}:clear`;
    return await this.doFetch(url, {
      body,
      method: 'POST',
    });
  }

  async getStreams(instance: string) {
    const url = `${this.apiUrl}/archive/${instance}/streams`;
    const response = await this.doFetch(url);
    const wrapper = await response.json() as StreamsWrapper;
    return await wrapper.streams || [];
  }

  async getStream(instance: string, name: string) {
    const url = `${this.apiUrl}/archive/${instance}/streams/${name}`;
    const response = await this.doFetch(url);
    return await response.json() as Stream;
  }

  async getTables(instance: string) {
    const url = `${this.apiUrl}/archive/${instance}/tables`;
    const response = await this.doFetch(url);
    const wrapper = await response.json() as TablesWrapper;
    return wrapper.tables || [];
  }

  async getTable(instance: string, name: string) {
    const url = `${this.apiUrl}/archive/${instance}/tables/${name}`;
    const response = await this.doFetch(url);
    return await response.json() as Table;
  }

  async getTableData(instance: string, name: string): Promise<Record[]> {
    const url = `${this.apiUrl}/archive/${instance}/tables/${name}/data`;
    const response = await this.doFetch(url);
    const wrapper = await response.json() as RecordsWrapper;
    return wrapper.record || [];
  }

  async executeSQL(database: string, statement: string): Promise<ResultSet> {
    const url = `${this.apiUrl}/archive/${database}:executeSql`;
    const response = await this.doFetch(url, {
      body: JSON.stringify({ instance: database, statement }),
      method: 'POST'
    });
    return await response.json() as ResultSet;
  }

  async getPacketNames(instance: string) {
    const url = `${this.apiUrl}/archive/${instance}/packet-names`;
    const response = await this.doFetch(url);
    return await response.json() as PacketNamesResponse;
  }

  async streamPacketIndex(instance: string, options: StreamPacketIndexOptions): Promise<ArchiveRecord[]> {
    const url = `${this.apiUrl}/archive/${instance}:streamPacketIndex`;
    const response = await this.doFetch(url, {
      body: JSON.stringify(options),
      method: 'POST',
    });
    // In-memory, we expect the stream to be limited by using an appropriate mergeTime
    const text = await response.text();
    const sanitized = '[' + text.replace(/}{/g, '},{') + ']';
    return JSON.parse(sanitized);
  }

  async streamParameterIndex(instance: string, options: StreamParameterIndexOptions): Promise<ArchiveRecord[]> {
    const url = `${this.apiUrl}/archive/${instance}:streamParameterIndex`;
    const response = await this.doFetch(url, {
      body: JSON.stringify(options),
      method: 'POST',
    });
    // In-memory, we expect the stream to be limited by using an appropriate mergeTime
    const text = await response.text();
    const sanitized = '[' + text.replace(/}{/g, '},{') + ']';
    return JSON.parse(sanitized);
  }

  async streamCommandIndex(instance: string, options: StreamCommandIndexOptions): Promise<ArchiveRecord[]> {
    const url = `${this.apiUrl}/archive/${instance}:streamCommandIndex`;
    const response = await this.doFetch(url, {
      body: JSON.stringify(options),
      method: 'POST',
    });
    // In-memory, we expect the stream to be limited by using an appropriate mergeTime
    const text = await response.text();
    const sanitized = '[' + text.replace(/}{/g, '},{') + ']';
    return JSON.parse(sanitized);
  }

  async streamEventIndex(instance: string, options: StreamEventIndexOptions): Promise<ArchiveRecord[]> {
    const url = `${this.apiUrl}/archive/${instance}:streamEventIndex`;
    const response = await this.doFetch(url, {
      body: JSON.stringify(options),
      method: 'POST',
    });
    // In-memory, we expect the stream to be limited by using an appropriate mergeTime
    const text = await response.text();
    const sanitized = '[' + text.replace(/}{/g, '},{') + ']';
    return JSON.parse(sanitized);
  }

  async getCompletenessIndex(instance: string, options: GetCompletenessIndexOptions): Promise<IndexGroup[]> {
    const url = `${this.apiUrl}/archive/${instance}/completeness-index`;
    const response = await this.doFetch(url + this.queryString(options));
    const wrapper = await response.json() as IndexResult;
    return wrapper.group || [];
  }

  async streamCompletenessIndex(instance: string, options: StreamCompletenessIndexOptions): Promise<ArchiveRecord[]> {
    const url = `${this.apiUrl}/archive/${instance}:streamCompletenessIndex`;
    const response = await this.doFetch(url, {
      body: JSON.stringify(options),
      method: 'POST',
    });
    // In-memory, we expect the stream to be limited by using an appropriate mergeTime
    const text = await response.text();
    const sanitized = '[' + text.replace(/}{/g, '},{') + ']';
    return JSON.parse(sanitized);
  }

  getPacketsDownloadURL(instance: string, options: DownloadPacketsOptions = {}) {
    const url = `${this.apiUrl}/archive/${instance}:exportPackets`;
    return url + this.queryString(options);
  }

  getPacketDownloadURL(instance: string, pname: string, gentime: string, seqnum: number) {
    const encodedName = encodeURIComponent(pname);
    return `${this.apiUrl}/archive/${instance}/packets/${encodedName}/${gentime}/${seqnum}:export`;
  }

  async getPacket(instance: string, pname: string, generationTime: string, sequenceNumber: number) {
    const encodedName = encodeURIComponent(pname);
    const url = `${this.apiUrl}/archive/${instance}/packets/${encodedName}/${generationTime}/${sequenceNumber}`;
    const response = await this.doFetch(url);
    return await response.json() as Packet;
  }

  async extractPacket(instance: string, pname: string, generationTime: string, sequenceNumber: number): Promise<ExtractPacketResponse> {
    const encodedName = encodeURIComponent(pname);
    const url = `${this.apiUrl}/archive/${instance}/packets/${encodedName}/${generationTime}/${sequenceNumber}:extract`;
    const response = await this.doFetch(url);
    return await response.json() as ExtractPacketResponse;
  }

  async getMissionDatabase(instance: string) {
    const url = `${this.apiUrl}/mdb/${instance}`;
    const response = await this.doFetch(url);
    return await response.json() as MissionDatabase;
  }

  async getSpaceSystems(instance: string) {
    const url = `${this.apiUrl}/mdb/${instance}/space-systems`;
    const response = await this.doFetch(url);
    return await response.json() as SpaceSystemsPage;
  }

  async getSpaceSystem(instance: string, qualifiedName: string) {
    const url = `${this.apiUrl}/mdb/${instance}/space-systems${qualifiedName}`;
    const response = await this.doFetch(url);
    return await response.json() as SpaceSystem;
  }

  async exportSpaceSystem(instance: string, qualifiedName: string) {
    const encodedName = encodeURIComponent(qualifiedName);
    const url = `${this.apiUrl}/mdb/${instance}/space-systems/${encodedName}:exportXTCE`;
    const response = await this.doFetch(url);
    return await response.text();
  }

  async getParameters(instance: string, options: GetParametersOptions = {}) {
    const url = `${this.apiUrl}/mdb/${instance}/parameters`;
    const response = await this.doFetch(url + this.queryString(options));
    return await response.json() as ParametersPage;
  }

  async getParameter(instance: string, qualifiedName: string) {
    const url = `${this.apiUrl}/mdb/${instance}/parameters${qualifiedName}`;
    const response = await this.doFetch(url);
    return await response.json() as Parameter;
  }

  async getParameterById(instance: string, id: NamedObjectId) {
    let url = `${this.apiUrl}/mdb/${instance}/parameters`;
    if (id.namespace) {
      url += '/' + encodeURIComponent(id.namespace);
      url += '/' + encodeURIComponent(id.name);
      const response = await this.doFetch(url);
      return await response.json() as Parameter;
    } else {
      return this.getParameter(instance, id.name);
    }
  }

  async getParameterTypes(instance: string, options: GetParameterTypesOptions = {}) {
    const url = `${this.apiUrl}/mdb/${instance}/parameter-types`;
    const response = await this.doFetch(url + this.queryString(options));
    return await response.json() as ParameterTypesPage;
  }

  async getParameterType(instance: string, qualifiedName: string) {
    const url = `${this.apiUrl}/mdb/${instance}/parameter-types${qualifiedName}`;
    const response = await this.doFetch(url);
    return await response.json() as ParameterType;
  }

  async getParameterValues(instance: string, qualifiedName: string, options: GetParameterValuesOptions = {}): Promise<ParameterValue[]> {
    const url = `${this.apiUrl}/archive/${instance}/parameters${qualifiedName}`;
    const response = await this.doFetch(url + this.queryString(options));
    const wrapper = await response.json() as ParameterData;
    return wrapper.parameter || [];
  }

  getParameterValuesDownloadURL(instance: string, options: DownloadParameterValuesOptions = {}) {
    const url = `${this.apiUrl}/archive/${instance}:exportParameterValues`;
    return url + this.queryString(options);
  }

  async exportParameterValues(instance: string, options: ExportParameterValuesOptions) {
    const url = `${this.apiUrl}/archive/${instance}:exportParameterValues`;
    const response = await this.doFetch(url, {
      method: 'POST',
      body: JSON.stringify(options),
    });
    return await response.text();
  }

  async setParameterValue(instance: string, processorName: string, qualifiedName: string, value: Value) {
    const url = `${this.apiUrl}/processors/${instance}/${processorName}/parameters${qualifiedName}`;
    return this.doFetch(url, {
      body: JSON.stringify(value),
      method: 'PUT',
    });
  }

  async getAlgorithmStatus(instance: string, processorName: string, qualifiedName: string) {
    const url = `${this.apiUrl}/processors/${instance}/${processorName}/algorithms${qualifiedName}/status`;
    const response = await this.doFetch(url);
    return await response.json() as AlgorithmStatus;
  }

  async getAlgorithmTrace(instance: string, processorName: string, qualifiedName: string) {
    const url = `${this.apiUrl}/processors/${instance}/${processorName}/algorithms${qualifiedName}/trace`;
    const response = await this.doFetch(url);
    return await response.json() as AlgorithmTrace;
  }

  async startAlgorithmTrace(instance: string, processorName: string, qualifiedName: string) {
    const url = `${this.apiUrl}/processors/${instance}/${processorName}/algorithms${qualifiedName}/trace`;
    return this.doFetch(url, {
      body: JSON.stringify({ "state": "enabled" }),
      method: 'PATCH',
    });
  }

  async stopAlgorithmTrace(instance: string, processorName: string, qualifiedName: string) {
    const url = `${this.apiUrl}/processors/${instance}/${processorName}/algorithms${qualifiedName}/trace`;
    return this.doFetch(url, {
      body: JSON.stringify({ "state": "disabled" }),
      method: 'PATCH',
    });
  }

  async updateAlgorithmText(instance: string, processorName: string, qualifiedName: string, text: string) {
    const url = `${this.apiUrl}/mdb/${instance}/${processorName}/algorithms${qualifiedName}`;
    return this.doFetch(url, {
      body: JSON.stringify({
        action: 'SET',
        algorithm: { text },
      }),
      method: 'PATCH',
    });
  }

  async getAlgorithmOverrides(instance: string, processorName: string, qualifiedName: string) {
    const url = `${this.apiUrl}/mdb-overrides/${instance}/${processorName}/algorithms${qualifiedName}`;
    const response = await this.doFetch(url);
    return await response.json() as AlgorithmOverrides;
  }

  async revertAlgorithmText(instance: string, processorName: string, qualifiedName: string) {
    const url = `${this.apiUrl}/mdb/${instance}/${processorName}/algorithms${qualifiedName}`;
    return this.doFetch(url, {
      body: JSON.stringify({
        action: 'RESET',
      }),
      method: 'PATCH',
    });
  }

  async getParameterSamples(instance: string, qualifiedName: string, options: GetParameterSamplesOptions = {}): Promise<Sample[]> {
    const url = `${this.apiUrl}/archive/${instance}/parameters${qualifiedName}/samples`;
    const response = await this.doFetch(url + this.queryString(options));
    const wrapper = await response.json() as SamplesWrapper;
    return wrapper.sample || [];
  }

  async getParameterRanges(instance: string, qualifiedName: string, options: GetParameterRangesOptions = {}): Promise<Range[]> {
    const url = `${this.apiUrl}/archive/${instance}/parameters${qualifiedName}/ranges`;
    const response = await this.doFetch(url + this.queryString(options));
    const wrapper = await response.json() as RangesWrapper;
    return wrapper.range || [];
  }

  async getCommands(instance: string, options: GetCommandsOptions = {}) {
    const url = `${this.apiUrl}/mdb/${instance}/commands`;
    const response = await this.doFetch(url + this.queryString(options));
    return await response.json() as CommandsPage;
  }

  async getCommand(instance: string, qualifiedName: string) {
    return this.getCommandForNamespace(instance, null, qualifiedName);
  }

  async getCommandForNamespace(instance: string, namespace: string | null, name: string) {
    const fullName = namespace ? namespace + "/" + name : name;
    const encodedName = encodeURIComponent(fullName);
    const url = `${this.apiUrl}/mdb/${instance}/commands/${encodedName}`;
    const response = await this.doFetch(url);
    return await response.json() as Command;
  }

  async getContainers(instance: string, options: GetContainersOptions = {}) {
    const url = `${this.apiUrl}/mdb/${instance}/containers`;
    const response = await this.doFetch(url + this.queryString(options));
    return await response.json() as ContainersPage;
  }

  async getContainer(instance: string, qualifiedName: string) {
    const url = `${this.apiUrl}/mdb/${instance}/containers${qualifiedName}`;
    const response = await this.doFetch(url);
    return await response.json() as Container;
  }

  async getActivities(instance: string, options: GetActivitiesOptions = {}) {
    const url = `${this.apiUrl}/activities/${instance}/activities`;
    const response = await this.doFetch(url + this.queryString(options));
    return await response.json() as ActivitiesPage;
  }

  async getActivity(instance: string, activityId: string) {
    const url = `${this.apiUrl}/activities/${instance}/activities/${activityId}`;
    const response = await this.doFetch(url);
    return await response.json() as Activity;
  }

  async getActivityLog(instance: string, activityId: string) {
    const url = `${this.apiUrl}/activities/${instance}/activities/${activityId}/log`;
    const response = await this.doFetch(url);
    const wrapper = await response.json() as GetActivityLogResponse;
    return wrapper.logs || [];
  }

  async startActivity(instance: string, options: StartActivityOptions) {
    const url = `${this.apiUrl}/activities/${instance}/activities`;
    const body = JSON.stringify(options);
    const response = await this.doFetch(url, {
      body,
      method: 'POST',
    });
    return await response.json() as Activity;
  }

  async cancelActivity(instance: string, activity: string) {
    const url = `${this.apiUrl}/activities/${instance}/activities/${activity}:cancel`;
    const response = await this.doFetch(url, {
      method: 'POST',
    });
    return await response.json() as Activity;
  }

  async completeManualActivity(instance: string, activity: string, options: CompleteManualActivityOptions = {}) {
    const url = `${this.apiUrl}/activities/${instance}/activities/${activity}:complete`;
    const body = JSON.stringify(options);
    const response = await this.doFetch(url, {
      body,
      method: 'POST',
    });
    return await response.json() as Activity;
  }

  async getExecutors(instance: string) {
    const url = `${this.apiUrl}/activities/${instance}/executors`;
    const response = await this.doFetch(url);
    const wrapper = await response.json() as ExecutorsWrapper;
    return wrapper.executors || [];
  }

  async getActivityScripts(instance: string) {
    const url = `${this.apiUrl}/activities/${instance}/scripts`;
    const response = await this.doFetch(url);
    return await response.json() as ActivityScriptsPage;
  }

  async getAlgorithms(instance: string, options: GetAlgorithmsOptions = {}) {
    const url = `${this.apiUrl}/mdb/${instance}/algorithms`;
    const response = await this.doFetch(url + this.queryString(options));
    return await response.json() as AlgorithmsPage;
  }

  async getAlgorithm(instance: string, qualifiedName: string) {
    const url = `${this.apiUrl}/mdb/${instance}/algorithms${qualifiedName}`;
    const response = await this.doFetch(url);
    return await response.json() as Algorithm;
  }

  async getFileTransferServices(instance: string) {
    const url = `${this.apiUrl}/filetransfer/${instance}/services`;
    const response = await this.doFetch(url);
    return await response.json() as ServicesPage;
  }

  async getFileTransfers(instance: string, service: string, options: GetFileTransfersOptions = {}) {
    const url = `${this.apiUrl}/filetransfer/${instance}/${service}/transfers`;
    const response = await this.doFetch(url + this.queryString(options));
    return await response.json() as TransfersPage;
  }

  async createFileTransfer(instance: string, service: string, request: CreateTransferRequest) {
    const url = `${this.apiUrl}/filetransfer/${instance}/${service}/transfers`;
    const body = JSON.stringify(request);
    const response = await this.doFetch(url, {
      body,
      method: 'POST',
    });
    return await response.json() as Transfer;
  }

  async pauseFileTransfer(instance: string, service: string, id: number) {
    const url = `${this.apiUrl}/filetransfer/${instance}/${service}/transfers/${id}:pause`;
    return this.doFetch(url, { method: 'POST' });
  }

  async resumeFileTransfer(instance: string, service: string, id: number) {
    const url = `${this.apiUrl}/filetransfer/${instance}/${service}/transfers/${id}:resume`;
    return this.doFetch(url, { method: 'POST' });
  }

  async cancelFileTransfer(instance: string, service: string, id: number) {
    const url = `${this.apiUrl}/filetransfer/${instance}/${service}/transfers/${id}:cancel`;
    return this.doFetch(url, { method: 'POST' });
  }

  async requestFileList(instance: string, service: string, request: ListFilesRequest) {
    const url = `${this.apiUrl}/filetransfer/${instance}/${service}/files:sync`;
    const body = JSON.stringify(request);
    return this.doFetch(url, { body, method: 'POST' });
  }

  async getFileList(instance: string, service: string, request: ListFilesRequest) {
    const url = `${this.apiUrl}/filetransfer/${instance}/${service}/files`;
    var indexedRequest: { [key: string]: any; } = request;
    const url2 = url + this.queryString(
      Object.keys(indexedRequest)
        .filter(key => key !== "options")
        .reduce((res: { [key: string]: any; }, key) => (res[key] = indexedRequest[key], res), {}))
      + (request.options ? "&options=" + JSON.stringify(request.options) : "");
    const response = await this.doFetch(url2);
    return await response.json() as ListFilesResponse;
  }

  async runFileAction(instance: string, service: string, request: RunFileActionRequest) {
    return this.doFetch(`${this.apiUrl}/filetransfer/${instance}/${service}/files:runFileAction`, {
      method: 'POST',
      body: JSON.stringify(request),
    });
  }

  async getCop1Config(instance: string, link: string) {
    const url = `${this.apiUrl}/cop1/${instance}/${link}/config`;
    const response = await this.doFetch(url);
    return await response.json() as Cop1Config;
  }

  async initiateCop1(instance: string, link: string, options: InitiateCop1Request) {
    const url = `${this.apiUrl}/cop1/${instance}/${link}:initialize`;
    const body = JSON.stringify(options);
    return this.doFetch(url, { body, method: 'POST' });
  }

  async disableCop1(instance: string, link: string, options: DisableCop1Request = {}) {
    const url = `${this.apiUrl}/cop1/${instance}/${link}:disable`;
    const body = JSON.stringify(options);
    return this.doFetch(url, { body, method: 'POST' });
  }

  async resumeCop1(instance: string, link: string) {
    const url = `${this.apiUrl}/cop1/${instance}/${link}:resume`;
    return await this.doFetch(url, { method: 'POST' });
  }

  async doFetch(url: string, init?: RequestInit) {
    if (this.accessToken) {
      if (!init) {
        init = { headers: new Headers() };
      } else if (!init.headers) {
        init.headers = new Headers();
      }
      const headers = init.headers as Headers;
      headers.append('Authorization', `Bearer ${this.accessToken}`);
    }

    let response: Response;
    try {
      if (this.interceptor) {
        response = await this.interceptor(this, url, init);
      } else {
        response = await this.handle(url, init);
      }
    } catch (err) { // NOTE: Fetch fails with "TypeError" on network or CORS failures.
      return Promise.reject(err);
    }

    // Make non 2xx responses available to clients via 'catch' instead of 'then'.
    if (response.ok) {
      return Promise.resolve(response);
    } else {
      return new Promise<Response>((resolve, reject) => {
        response.json().then(json => {
          if (json.hasOwnProperty("detail")) {
            reject(new HttpError(response, json['msg'], json['detail']));
          } else {
            reject(new HttpError(response, json['msg']));
          }
        }).catch(err => {
          console.error('Failure while handling server error', err);
          reject(new HttpError(response));
        });
      });
    }
  }

  handle(url: string, init?: RequestInit) {
    // Our handler uses Fetch API
    return fetch(url, init);
  }

  prepareWebSocketClient() {
    if (!this.webSocketClient) {
      this.webSocketClient = new WebSocketClient(this.apiUrl, this.frameLossListener);
      // Copy connection updates from the WebSocketConnection to the subject in
      // this class. Our local subject must always be available even when the
      // WebSocket was not yet set up (for example because auth is still
      // required).
      this.webSocketClient.connected$.subscribe(connected => {
        if (connected) {
          this.createSessionSubscription(evt => {
            this.sessionListener.onSessionEnd(evt.endReason);
          });
        }
        this.connected$.next(connected);
      });
    }
  }

  getWebSocketClient() {
    return this.webSocketClient;
  }

  closeWebSocketClient() {
    if (this.webSocketClient) {
      this.webSocketClient.close();
      this.webSocketClient = undefined;
    }
  }

  queryString(options: { [key: string]: any; }) {
    const qs = Object.keys(options)
      .filter(k => options[k] !== undefined)
      .map(k => `${k}=${options[k]}`)
      .join('&');
    return qs === '' ? qs : '?' + qs;
  }
}
