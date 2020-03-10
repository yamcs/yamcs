import { Observable } from 'rxjs';
import { AlgorithmsPage, Command, CommandsPage, Container, ContainersPage, CreateTransferRequest, GetAlgorithmsOptions, GetCommandsOptions, GetContainersOptions, GetParametersOptions, GetSpaceSystemsOptions, MissionDatabase, NamedObjectId, Parameter, ParametersPage, SpaceSystem, SpaceSystemsPage, Transfer, TransfersPage } from '.';
import { HttpError } from './HttpError';
import { HttpHandler } from './HttpHandler';
import { HttpInterceptor } from './HttpInterceptor';
import { Alarm, AlarmSubscription, EditAlarmOptions, GetAlarmsOptions, GlobalAlarmStatus, GlobalAlarmStatusSubscription, SubscribeAlarmsRequest, SubscribeGlobalAlarmStatusRequest } from './types/alarms';
import { CommandSubscription, SubscribeCommandsRequest } from './types/commandHistory';
import { Cop1Config, Cop1Status, Cop1Subscription, SubscribeCop1Request } from './types/cop1';
import { CreateEventRequest, DownloadEventsOptions, Event, EventSubscription, GetEventsOptions, SubscribeEventsRequest } from './types/events';
import { AlarmsWrapper, ClientConnectionsWrapper, CommandQueuesWrapper, EventsWrapper, GroupsWrapper, IndexResult, InstancesWrapper, InstanceTemplatesWrapper, LinksWrapper, PacketNameWrapper, ProcessorsWrapper, RangesWrapper, RecordsWrapper, RocksDbDatabasesWrapper, RolesWrapper, SamplesWrapper, ServicesWrapper, SourcesWrapper, SpaceSystemsWrapper, StreamsWrapper, TablesWrapper, UsersWrapper } from './types/internal';
import { CreateInstanceRequest, EditLinkOptions, InstancesSubscription, Link, LinkEvent, LinkSubscription, ListInstancesOptions, SubscribeLinksRequest } from './types/management';
import { CommandHistoryEntry, CommandHistoryPage, CreateProcessorRequest, DownloadPacketsOptions, DownloadParameterValuesOptions, EditReplayProcessorRequest, ExportXtceOptions, GetCommandHistoryOptions, GetCommandIndexOptions, GetCompletenessIndexOptions, GetEventIndexOptions, GetPacketIndexOptions, GetPacketsOptions, GetParameterIndexOptions, GetParameterRangesOptions, GetParameterSamplesOptions, GetParameterValuesOptions, GetTagsOptions, IndexGroup, IssueCommandOptions, IssueCommandResponse, ListGapsResponse, ListPacketsResponse, ParameterData, ParameterValue, Range, RequestPlaybackRequest, Sample, TagsPage, Value } from './types/monitoring';
import { ParameterSubscription, Processor, ProcessorSubscription, Statistics, SubscribeParametersData, SubscribeParametersRequest, SubscribeProcessorsRequest, SubscribeTMStatisticsRequest, TMStatisticsSubscription } from './types/processing';
import { CommandQueue, CommandQueueEvent, EditCommandQueueEntryOptions, EditCommandQueueOptions, QueueEventsSubscription, QueueStatisticsSubscription, SubscribeQueueEventsRequest, SubscribeQueueStatisticsRequest } from './types/queue';
import { AuthInfo, CreateGroupRequest, CreateServiceAccountRequest, CreateServiceAccountResponse, CreateUserRequest, EditClearanceRequest, EditGroupRequest, EditUserRequest, GeneralInfo, GroupInfo, Instance, InstanceTemplate, LeapSecondsTable, ListClearancesResponse, ListProcessorTypesResponse, ListRoutesResponse, ListServiceAccountsResponse, ListTopicsResponse, RoleInfo, Service, ServiceAccount, SystemInfo, TokenResponse, UserInfo } from './types/system';
import { Record, Stream, StreamData, StreamEvent, StreamStatisticsSubscription, StreamSubscription, SubscribeStreamRequest, SubscribeStreamStatisticsRequest, Table } from './types/table';
import { SubscribeTimeRequest, Time, TimeSubscription } from './types/time';
import { WebSocketClient } from './WebSocketClient';


export default class YamcsClient implements HttpHandler {

  readonly apiUrl: string;
  readonly authUrl: string;
  readonly staticUrl: string;

  private accessToken?: string;

  private interceptor: HttpInterceptor;

  public connected$: Observable<boolean>;
  private webSocketClient?: WebSocketClient;

  constructor(readonly baseHref = '/') {
    this.apiUrl = `${this.baseHref}api`;
    this.authUrl = `${this.baseHref}auth`;
    this.staticUrl = `${this.baseHref}static`;
  }

  createInstancesSubscription(observer: (instance: Instance) => void): InstancesSubscription {
    this.prepareWebSocketClient();
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

  async deleteIdentity(username: string, provider: string) {
    const url = `${this.apiUrl}/users/${username}/identities/${provider}`;
    const response = await this.doFetch(url, { method: 'DELETE' });
    return await response.json() as UserInfo;
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

  async getClientConnections() {
    const url = `${this.apiUrl}/connections`;
    const response = await this.doFetch(url);
    const wrapper = await response.json() as ClientConnectionsWrapper;
    return wrapper.connections || [];
  }

  async closeClientConnection(id: string) {
    const url = `${this.apiUrl}/connections/${id}`;
    return await this.doFetch(url, { method: 'DELETE' });
  }

  createTimeSubscription(options: SubscribeTimeRequest, observer: (time: Time) => void): TimeSubscription {
    this.prepareWebSocketClient();
    return this.webSocketClient!.createSubscription('time', options, observer);
  }

  createProcessorSubscription(options: SubscribeProcessorsRequest, observer: (processor: Processor) => void): ProcessorSubscription {
    this.prepareWebSocketClient();
    return this.webSocketClient!.createSubscription('processors', options, observer);
  }

  createCop1Subscription(options: SubscribeCop1Request, observer: (cop1Status: Cop1Status) => void): Cop1Subscription {
    this.prepareWebSocketClient();
    return this.webSocketClient!.createSubscription('cop1', options, observer);
  }

  createGlobalAlarmStatusSubscription(options: SubscribeGlobalAlarmStatusRequest, observer: (status: GlobalAlarmStatus) => void): GlobalAlarmStatusSubscription {
    this.prepareWebSocketClient();
    return this.webSocketClient!.createSubscription('global-alarm-status', options, observer);
  }

  createTMStatisticsSubscription(options: SubscribeTMStatisticsRequest, observer: (time: Statistics) => void): TMStatisticsSubscription {
    this.prepareWebSocketClient();
    return this.webSocketClient!.createSubscription('tmstats', options, observer);
  }

  createEventSubscription(options: SubscribeEventsRequest, observer: (event: Event) => void): EventSubscription {
    this.prepareWebSocketClient();
    return this.webSocketClient!.createSubscription('events', options, observer);
  }

  createLinkSubscription(options: SubscribeLinksRequest, observer: (linkEvent: LinkEvent) => void): LinkSubscription {
    this.prepareWebSocketClient();
    return this.webSocketClient!.createSubscription('links', options, observer);
  }

  createParameterSubscription(options: SubscribeParametersRequest, observer: (data: SubscribeParametersData) => void): ParameterSubscription {
    this.prepareWebSocketClient();
    return this.webSocketClient!.createSubscription('parameters', options, observer);
  }

  createStreamStatisticsSubscription(options: SubscribeStreamStatisticsRequest, observer: (event: StreamEvent) => void): StreamStatisticsSubscription {
    this.prepareWebSocketClient();
    return this.webSocketClient!.createSubscription('stream-stats', options, observer);
  }

  createStreamSubscription(options: SubscribeStreamRequest, observer: (data: StreamData) => void): StreamSubscription {
    this.prepareWebSocketClient();
    return this.webSocketClient!.createSubscription('stream', options, observer);
  }

  createQueueStatisticsSubscription(options: SubscribeQueueStatisticsRequest, observer: (queue: CommandQueue) => void): QueueStatisticsSubscription {
    this.prepareWebSocketClient();
    return this.webSocketClient!.createSubscription('queue-stats', options, observer);
  }

  createQueueEventsSubscription(options: SubscribeQueueEventsRequest, observer: (event: CommandQueueEvent) => void): QueueEventsSubscription {
    this.prepareWebSocketClient();
    return this.webSocketClient!.createSubscription('queue-events', options, observer);
  }

  createAlarmSubscription(options: SubscribeAlarmsRequest, observer: (alarm: Alarm) => void): AlarmSubscription {
    this.prepareWebSocketClient();
    return this.webSocketClient!.createSubscription('alarms', options, observer);
  }

  createCommandSubscription(options: SubscribeCommandsRequest, observer: (entry: CommandHistoryEntry) => void): CommandSubscription {
    this.prepareWebSocketClient();
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

  async compactRocksDbDatabase(tablespace: string, dbPath = '') {
    const url = `${this.apiUrl}/archive/rocksdb/${tablespace}/${dbPath}:compact`;
    return await this.doFetch(url, {
      method: 'POST',
    });
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
    const url = `${this.apiUrl}/archive/${instance}/events`;
    const response = await this.doFetch(url + this.queryString(options));
    const wrapper = await response.json() as EventsWrapper;
    return wrapper.event || [];
  }

  /**
   * Retrieves the distinct sources for the currently archived events.
   */
  async getEventSources(instance: string) {
    const url = `${this.apiUrl}/archive/${instance}/events/sources`;
    const response = await this.doFetch(url);
    const wrapper = await response.json() as SourcesWrapper;
    return wrapper.source || [];
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

  async getMissionDatabase(instance: string) {
    const url = `${this.apiUrl}/mdb/${instance}`;
    const response = await this.doFetch(url);
    return await response.json() as MissionDatabase;
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

  async enableLink(instance: string, name: string) {
    return this.editLink(instance, name, { state: 'enabled' });
  }

  async disableLink(instance: string, name: string) {
    return this.editLink(instance, name, { state: 'disabled' });
  }

  async editLink(instance: string, name: string, options: EditLinkOptions) {
    const body = JSON.stringify(options);
    return this.doFetch(`${this.apiUrl}/links/${instance}/${name}`, {
      body,
      method: 'PATCH',
    });
  }

  async getProcessors(instance: string) {
    const url = `${this.apiUrl}/processors/${instance}`;
    const response = await this.doFetch(url);
    const wrapper = await response.json() as ProcessorsWrapper;
    return wrapper.processor || [];
  }

  async getProcessor(instance: string, name: string) {
    const url = `${this.apiUrl}/processors/${instance}/${name}`;
    const response = await this.doFetch(url);
    return await response.json() as Processor;
  }

  async issueCommand(instance: string, processorName: string, qualifiedName: string, options?: IssueCommandOptions): Promise<IssueCommandResponse> {
    const body = JSON.stringify(options);
    const response = await this.doFetch(`${this.apiUrl}/processors/${instance}/${processorName}/commands${qualifiedName}`, {
      body,
      method: 'POST',
    });
    return await response.json() as IssueCommandResponse;
  }

  async getCommandHistoryEntry(instance: string, id: string): Promise<CommandHistoryEntry> {
    const url = `${this.apiUrl}/archive/${instance}/commands/${id}`;
    const response = await this.doFetch(url);
    return await response.json() as CommandHistoryEntry;
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

  async getCommandQueues(instance: string, processorName: string) {
    const url = `${this.apiUrl}/processors/${instance}/${processorName}/queues`;
    const response = await this.doFetch(url);
    const wrapper = await response.json() as CommandQueuesWrapper;
    return wrapper.queues || [];
  }

  async getCommandQueue(instance: string, processorName: string, queueName: string) {
    const url = `${this.apiUrl}/processors/${instance}/${processorName}/queues/${queueName}`;
    const response = await this.doFetch(url);
    return await response.json() as CommandQueue;
  }

  async editCommandQueue(instance: string, processorName: string, queueName: string, options: EditCommandQueueOptions) {
    const url = `${this.apiUrl}/processors/${instance}/${processorName}/queues/${queueName}`;
    const body = JSON.stringify(options);
    const response = await this.doFetch(url, {
      body,
      method: 'PATCH',
    });
    return await response.json() as CommandQueue;
  }

  async editCommandQueueEntry(instance: string, processorName: string, queueName: string, uuid: string, options: EditCommandQueueEntryOptions) {
    const url = `${this.apiUrl}/processors/${instance}/${processorName}/queues/${queueName}/entries/${uuid}`;
    const body = JSON.stringify(options);
    const response = await this.doFetch(url, {
      body,
      method: 'PATCH',
    });
  }

  async getActiveAlarms(instance: string, processorName: string, options: GetAlarmsOptions = {}): Promise<Alarm[]> {
    const url = `${this.apiUrl}/processors/${instance}/${processorName}/alarms`;
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

  async editAlarm(instance: string, processor: string, alarm: string, sequenceNumber: number, options: EditAlarmOptions) {
    const body = JSON.stringify(options);
    const url = `${this.apiUrl}/processors/${instance}/${processor}/alarms${alarm}/${sequenceNumber}`;
    return await this.doFetch(url, {
      body,
      method: 'PATCH',
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

  async getPacketNames(instance: string) {
    const url = `${this.apiUrl}/archive/${instance}/packet-names`;
    const response = await this.doFetch(url);
    const wrapper = await response.json() as PacketNameWrapper;
    return wrapper.name || [];
  }

  async getPacketIndex(instance: string, options: GetPacketIndexOptions): Promise<IndexGroup[]> {
    const url = `${this.apiUrl}/archive/${instance}/packet-index`;
    const response = await this.doFetch(url + this.queryString(options));
    const wrapper = await response.json() as IndexResult;
    return wrapper.group || [];
  }

  async getParameterIndex(instance: string, options: GetParameterIndexOptions): Promise<IndexGroup[]> {
    const url = `${this.apiUrl}/archive/${instance}/parameter-index`;
    const response = await this.doFetch(url + this.queryString(options));
    const wrapper = await response.json() as IndexResult;
    return wrapper.group || [];
  }

  async getCommandIndex(instance: string, options: GetCommandIndexOptions): Promise<IndexGroup[]> {
    const url = `${this.apiUrl}/archive/${instance}/command-index`;
    const response = await this.doFetch(url + this.queryString(options));
    const wrapper = await response.json() as IndexResult;
    return wrapper.group || [];
  }

  async getEventIndex(instance: string, options: GetEventIndexOptions): Promise<IndexGroup[]> {
    const url = `${this.apiUrl}/archive/${instance}/event-index`;
    const response = await this.doFetch(url + this.queryString(options));
    const wrapper = await response.json() as IndexResult;
    return wrapper.group || [];
  }

  async getCompletenessIndex(instance: string, options: GetCompletenessIndexOptions): Promise<IndexGroup[]> {
    const url = `${this.apiUrl}/archive/${instance}/completeness-index`;
    const response = await this.doFetch(url + this.queryString(options));
    const wrapper = await response.json() as IndexResult;
    return wrapper.group || [];
  }

  getPacketsDownloadURL(instance: string, options: DownloadPacketsOptions = {}) {
    const url = `${this.apiUrl}/archive/${instance}:exportPackets`;
    return url + this.queryString(options);
  }

  getXtceDownloadURL(instance: string, options: ExportXtceOptions = {}) {
    const url = `${this.apiUrl}/mdb/${instance}/space-systems${options.spaceSystem}:exportXTCE`;
    return url + this.queryString(options);
  }

  async getRootSpaceSystems(instance: string, ) {
    const url = `${this.apiUrl}/mdb/${instance}`;
    const response = await this.doFetch(url);
    const wrapper = await response.json() as SpaceSystemsWrapper;
    return wrapper.spaceSystems || [];
  }

  async getSpaceSystems(instance: string, options: GetSpaceSystemsOptions = {}) {
    const url = `${this.apiUrl}/mdb/${instance}/space-systems`;
    const response = await this.doFetch(url + this.queryString(options));
    return await response.json() as SpaceSystemsPage;
  }

  async getSpaceSystem(instance: string, qualifiedName: string) {
    const url = `${this.apiUrl}/mdb/${instance}/space-systems${qualifiedName}`;
    const response = await this.doFetch(url);
    return await response.json() as SpaceSystem;
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

  async setParameterValue(instance: string, processorName: string, qualifiedName: string, value: Value) {
    const url = `${this.apiUrl}/processors/${instance}/${processorName}/parameters${qualifiedName}`;
    return this.doFetch(url, {
      body: JSON.stringify(value),
      method: 'PUT',
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
    const url = `${this.apiUrl}/mdb/${instance}/commands${qualifiedName}`;
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

  async getTags(instance: string, options: GetTagsOptions = {}) {
    const url = `${this.apiUrl}/archive/${instance}/tags`;
    const response = await this.doFetch(url + this.queryString(options));
    return await response.json() as TagsPage;
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

  async getCfdpTransfers(instance: string, ) {
    const url = `${this.apiUrl}/cfdp/${instance}/transfers`;
    const response = await this.doFetch(url);
    return await response.json() as TransfersPage;
  }

  async createCfdpTransfer(instance: string, options: CreateTransferRequest) {
    const url = `${this.apiUrl}/cfdp/${instance}/transfers`;
    const body = JSON.stringify(options);
    const response = await this.doFetch(url, {
      body,
      method: 'POST',
    });
    return await response.json() as Transfer;
  }

  async getGaps(instance: string, ) {
    const url = `${this.apiUrl}/dass/gaps/${instance}`;
    const response = await this.doFetch(url);
    return await response.json() as ListGapsResponse;
  }

  async requestPlayback(instance: string, link: string, options: RequestPlaybackRequest) {
    const url = `${this.apiUrl}/dass/links/${instance}/${link}:requestPlayback`;
    const body = JSON.stringify(options);
    return await this.doFetch(url, {
      body,
      method: 'POST',
    });
  }

  async getCop1Config(instance: string, link: string) {
    const url = `${this.apiUrl}/cop1/${instance}/${link}/config`;
    const response = await this.doFetch(url);
    return await response.json() as Cop1Config;
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
          reject(new HttpError(response, json['msg']));
        }).catch(err => {
          console.error('Failure while handling server error', err);
          reject(new HttpError(response));
        });
      });
    }
  }

  handle(url: string, init?: RequestInit) {
    // Our handler uses Fetch API, available in modern browsers.
    // For older browsers, the end application should include an
    // appropriate polyfill.
    return fetch(url, init);
  }

  prepareWebSocketClient() {
    if (!this.webSocketClient) {
      this.webSocketClient = new WebSocketClient(this.apiUrl);
      this.connected$ = this.webSocketClient.connected$;
    }
  }

  private queryString(options: { [key: string]: any; }) {
    const qs = Object.keys(options)
      .map(k => `${k}=${options[k]}`)
      .join('&');
    return qs === '' ? qs : '?' + qs;
  }
}
