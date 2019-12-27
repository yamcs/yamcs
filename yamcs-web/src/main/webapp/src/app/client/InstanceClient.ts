import { Observable } from 'rxjs';
import { CreateTransferRequest, Transfer, TransfersPage } from './types/cfdp';
import { AlarmsWrapper, ClientsWrapper, CommandQueuesWrapper, EventsWrapper, IndexResult, LinksWrapper, PacketNameWrapper, ProcessorsWrapper, RangesWrapper, RecordsWrapper, SamplesWrapper, ServicesWrapper, SourcesWrapper, SpaceSystemsWrapper, StreamsWrapper, TablesWrapper } from './types/internal';
import { Algorithm, AlgorithmsPage, Command, CommandsPage, Container, ContainersPage, GetAlgorithmsOptions, GetCommandsOptions, GetContainersOptions, GetParametersOptions, MissionDatabase, NamedObjectId, Parameter, ParametersPage, SpaceSystem, SpaceSystemsPage } from './types/mdb';
import { Alarm, AlarmSubscriptionResponse, CommandHistoryEntry, CommandHistoryPage, CreateEventRequest, CreateProcessorRequest, DownloadEventsOptions, DownloadPacketsOptions, DownloadParameterValuesOptions, EditAlarmOptions, EditReplayProcessorRequest, Event, EventSubscriptionResponse, GetAlarmsOptions, GetCommandHistoryOptions, GetCommandIndexOptions, GetCompletenessIndexOptions, GetEventIndexOptions, GetEventsOptions, GetPacketIndexOptions, GetPacketsOptions, GetParameterIndexOptions, GetParameterRangesOptions, GetParameterSamplesOptions, GetParameterValuesOptions, GetTagsOptions, IndexGroup, IssueCommandOptions, IssueCommandResponse, ListGapsResponse, ListPacketsResponse, ParameterData, ParameterSubscriptionRequest, ParameterSubscriptionResponse, ParameterValue, Range, RequestPlaybackRequest, Sample, TagsPage, TimeSubscriptionResponse, Value } from './types/monitoring';
import { ClientInfo, ClientSubscriptionResponse, CommandQueue, CommandQueueEventSubscriptionResponse, CommandQueueSubscriptionResponse, CommandSubscriptionRequest, CommandSubscriptionResponse, ConnectionInfoSubscriptionResponse, EditCommandQueueEntryOptions, EditCommandQueueOptions, EditLinkOptions, InstanceSubscriptionResponse, Link, LinkSubscriptionResponse, Processor, ProcessorSubscriptionResponse, Record, Service, StatisticsSubscriptionResponse, Stream, StreamEventSubscriptionResponse, StreamSubscriptionResponse, Table } from './types/system';
import { WebSocketClient } from './WebSocketClient';
import YamcsClient from './YamcsClient';

export class InstanceClient {

  public connected$: Observable<boolean>;
  private webSocketClient?: WebSocketClient;

  constructor(
    readonly instance: string,
    private yamcs: YamcsClient) {
  }

  async getTimeUpdates(): Promise<TimeSubscriptionResponse> {
    this.prepareWebSocketClient();
    return this.webSocketClient!.getTimeUpdates();
  }

  async getInstanceUpdates(): Promise<InstanceSubscriptionResponse> {
    this.prepareWebSocketClient();
    return this.webSocketClient!.getInstanceUpdates();
  }

  async getPackets(options: GetPacketsOptions = {}) {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/packets`;
    const response = await this.yamcs.doFetch(url + this.queryString(options));
    return await response.json() as ListPacketsResponse;
  }

  async getEvents(options: GetEventsOptions = {}) {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/events`;
    const response = await this.yamcs.doFetch(url + this.queryString(options));
    const wrapper = await response.json() as EventsWrapper;
    return wrapper.event || [];
  }

  /**
   * Retrieves the distinct sources for the currently archived events.
   */
  async getEventSources() {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/events/sources`;
    const response = await this.yamcs.doFetch(url);
    const wrapper = await response.json() as SourcesWrapper
    return wrapper.source || [];
  }

  getEventsDownloadURL(options: DownloadEventsOptions = {}) {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}:exportEvents`;
    return url + this.queryString(options);
  }

  async getEventUpdates(): Promise<EventSubscriptionResponse> {
    this.prepareWebSocketClient();
    return this.webSocketClient!.getEventUpdates();
  }

  async unsubscribeEventUpdates() {
    return this.webSocketClient!.unsubscribeEventUpdates();
  }

  async createEvent(options: CreateEventRequest) {
    const body = JSON.stringify(options);
    const response = await this.yamcs.doFetch(`${this.yamcs.apiUrl}/archive/${this.instance}/events`, {
      body,
      method: 'POST',
    });
    return await response.json() as Event;
  }

  async createProcessor(options: CreateProcessorRequest) {
    const body = JSON.stringify(options);
    return await this.yamcs.doFetch(`${this.yamcs.apiUrl}/processors/${this.instance}`, {
      body,
      method: 'POST',
    });
  }

  async editReplayProcessor(processor: string, options: EditReplayProcessorRequest) {
    const body = JSON.stringify(options);
    const url = `${this.yamcs.apiUrl}/processors/${this.instance}/${processor}`;
    return await this.yamcs.doFetch(url, {
      body,
      method: 'PATCH',
    });
  }

  async deleteReplayProcessor(processor: string) {
    const url = `${this.yamcs.apiUrl}/processors/${this.instance}/${processor}`;
    return await this.yamcs.doFetch(url, {
      method: 'DELETE',
    });
  }

  async getMissionDatabase() {
    const url = `${this.yamcs.apiUrl}/mdb/${this.instance}`;
    const response = await this.yamcs.doFetch(url);
    return await response.json() as MissionDatabase;
  }

  async getLinks(): Promise<Link[]> {
    const url = `${this.yamcs.apiUrl}/links/${this.instance}`
    const response = await this.yamcs.doFetch(url);
    const wrapper = await response.json() as LinksWrapper;
    return wrapper.links || [];
  }

  async getLink(name: string): Promise<Link> {
    const url = `${this.yamcs.apiUrl}/links/${this.instance}/${name}`
    const response = await this.yamcs.doFetch(url);
    return await response.json() as Link;
  }

  /**
   * Returns Connection Info messages whenever a major event on
   * the websocket connection happens. This includes:
   * - Changed active instance
   * - Changed active processor
   * - Restart of connected instance
   *
   * Note especially that this does not provide info about the connection
   * state itself (e.g. no disconnect event).
   */
  async getConnectionInfoUpdates(): Promise<ConnectionInfoSubscriptionResponse> {
    this.prepareWebSocketClient();
    return this.webSocketClient!.getConnectionInfoUpdates();
  }

  async getLinkUpdates(): Promise<LinkSubscriptionResponse> {
    this.prepareWebSocketClient();
    return this.webSocketClient!.getLinkUpdates(this.instance);
  }

  async unsubscribeLinkUpdates() {
    return this.webSocketClient!.unsubscribeLinkUpdates();
  }

  async enableLink(name: string) {
    return this.editLink(name, { state: 'enabled' })
  }

  async disableLink(name: string) {
    return this.editLink(name, { state: 'disabled' })
  }

  async editLink(name: string, options: EditLinkOptions) {
    const body = JSON.stringify(options);
    return this.yamcs.doFetch(`${this.yamcs.apiUrl}/links/${this.instance}/${name}`, {
      body,
      method: 'PATCH',
    });
  }

  async getStreamEventUpdates(): Promise<StreamEventSubscriptionResponse> {
    this.prepareWebSocketClient();
    return this.webSocketClient!.getStreamEventUpdates(this.instance);
  }

  async unsubscribeStreamEventUpdates() {
    return this.webSocketClient!.unsubscribeStreamEventUpdates();
  }

  async getProcessors() {
    const url = `${this.yamcs.apiUrl}/processors/${this.instance}`;
    const response = await this.yamcs.doFetch(url);
    const wrapper = await response.json() as ProcessorsWrapper;
    return wrapper.processor || [];
  }

  async getProcessor(name: string) {
    const url = `${this.yamcs.apiUrl}/processors/${this.instance}/${name}`;
    const response = await this.yamcs.doFetch(url);
    return await response.json() as Processor;
  }

  async getProcessorUpdates(): Promise<ProcessorSubscriptionResponse> {
    this.prepareWebSocketClient();
    return this.webSocketClient!.getProcessorUpdates()
  }

  async getProcessorStatistics(): Promise<StatisticsSubscriptionResponse> {
    this.prepareWebSocketClient();
    return this.webSocketClient!.getProcessorStatistics(this.instance);
  }

  async getCommandUpdates(options: CommandSubscriptionRequest = {}): Promise<CommandSubscriptionResponse> {
    this.prepareWebSocketClient();
    return this.webSocketClient!.getCommandUpdates(options);
  }

  async unsubscribeCommandUpdates() {
    return this.webSocketClient!.unsubscribeCommandUpdates();
  }

  async issueCommand(processorName: string, qualifiedName: string, options?: IssueCommandOptions): Promise<IssueCommandResponse> {
    const body = JSON.stringify(options);
    const response = await this.yamcs.doFetch(`${this.yamcs.apiUrl}/processors/${this.instance}/${processorName}/commands${qualifiedName}`, {
      body,
      method: 'POST',
    });
    return await response.json() as IssueCommandResponse;
  }

  async getCommandHistoryEntry(id: string): Promise<CommandHistoryEntry> {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/commands/${id}`;
    const response = await this.yamcs.doFetch(url);
    return await response.json() as CommandHistoryEntry;
  }

  async getCommandHistoryEntries(options: GetCommandHistoryOptions = {}): Promise<CommandHistoryPage> {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/commands`;
    const response = await this.yamcs.doFetch(url + this.queryString(options));
    return await response.json() as CommandHistoryPage;
  }

  async getCommandHistoryEntriesForParameter(qualifiedName: string, options: GetCommandHistoryOptions = {}) {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/commands${qualifiedName}`;
    const response = await this.yamcs.doFetch(url + this.queryString(options));
    return await response.json() as CommandHistoryPage;
  }

  async getCommandQueues(processorName: string) {
    const url = `${this.yamcs.apiUrl}/processors/${this.instance}/${processorName}/cqueues`;
    const response = await this.yamcs.doFetch(url);
    const wrapper = await response.json() as CommandQueuesWrapper;
    return wrapper.queue || [];
  }

  async getCommandQueue(processorName: string, queueName: string) {
    const url = `${this.yamcs.apiUrl}/processors/${this.instance}/${processorName}/cqueues/${queueName}`;
    const response = await this.yamcs.doFetch(url);
    return await response.json() as CommandQueue;
  }

  async getCommandQueueUpdates(processorName?: string): Promise<CommandQueueSubscriptionResponse> {
    this.prepareWebSocketClient();
    return this.webSocketClient!.getCommandQueueUpdates(this.instance, processorName);
  }

  async editCommandQueue(processorName: string, queueName: string, options: EditCommandQueueOptions) {
    const url = `${this.yamcs.apiUrl}/processors/${this.instance}/${processorName}/cqueues/${queueName}`;
    const body = JSON.stringify(options);
    const response = await this.yamcs.doFetch(url, {
      body,
      method: 'PATCH',
    });
    return await response.json() as CommandQueue;
  }

  async getCommandQueueEventUpdates(processorName?: string): Promise<CommandQueueEventSubscriptionResponse> {
    this.prepareWebSocketClient();
    return this.webSocketClient!.getCommandQueueEventUpdates(this.instance, processorName);
  }

  async editCommandQueueEntry(processorName: string, queueName: string, uuid: string, options: EditCommandQueueEntryOptions) {
    const url = `${this.yamcs.apiUrl}/processors/${this.instance}/${processorName}/cqueues/${queueName}/entries/${uuid}`;
    const body = JSON.stringify(options);
    const response = await this.yamcs.doFetch(url, {
      body,
      method: 'PATCH',
    });
  }

  async getClients() {
    const url = `${this.yamcs.apiUrl}/instances/${this.instance}/clients`;
    const response = await this.yamcs.doFetch(url);
    const wrapper = await response.json() as ClientsWrapper;
    return wrapper.client || [];
  }

  async getClient(id: number) {
    const url = `${this.yamcs.apiUrl}/clients/${id}`;
    const response = await this.yamcs.doFetch(url);
    return await response.json() as ClientInfo;
  }

  async getClientUpdates(anyInstance = false): Promise<ClientSubscriptionResponse> {
    this.prepareWebSocketClient();
    if (anyInstance) {
      return this.webSocketClient!.getClientUpdates();
    } else {
      return this.webSocketClient!.getClientUpdates(this.instance);
    }
  }

  async getServices(): Promise<Service[]> {
    const url = `${this.yamcs.apiUrl}/services/${this.instance}`;
    const response = await this.yamcs.doFetch(url);
    const wrapper = await response.json() as ServicesWrapper;
    return wrapper.services || [];
  }

  async getService(name: string): Promise<Service> {
    const url = `${this.yamcs.apiUrl}/services/${this.instance}/${name}`;
    const response = await this.yamcs.doFetch(url);
    return await response.json() as Service;
  }

  async startService(name: string) {
    return this.yamcs.doFetch(`${this.yamcs.apiUrl}/services/${this.instance}/${name}:start`, {
      method: 'POST',
    });
  }

  async stopService(name: string) {
    return this.yamcs.doFetch(`${this.yamcs.apiUrl}/services/${this.instance}/${name}:stop`, {
      method: 'POST',
    });
  }

  async getActiveAlarms(processorName: string, options: GetAlarmsOptions = {}): Promise<Alarm[]> {
    const url = `${this.yamcs.apiUrl}/processors/${this.instance}/${processorName}/alarms`;
    const response = await this.yamcs.doFetch(url + this.queryString(options));
    const wrapper = await response.json() as AlarmsWrapper;
    return wrapper.alarm || [];
  }

  async getAlarms(options: GetAlarmsOptions = {}) {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/alarms`;
    const response = await this.yamcs.doFetch(url + this.queryString(options));
    const wrapper = await response.json() as AlarmsWrapper;
    return wrapper.alarm || [];
  }

  async getAlarmUpdates(): Promise<AlarmSubscriptionResponse> {
    this.prepareWebSocketClient();
    return this.webSocketClient!.getAlarmUpdates({
      detail: true,
    });
  }

  async getAlarmsForParameter(qualifiedName: string, options: GetAlarmsOptions = {}) {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/alarms${qualifiedName}`;
    const response = await this.yamcs.doFetch(url + this.queryString(options));
    const wrapper = await response.json() as AlarmsWrapper;
    return await wrapper.alarm || [];
  }

  async editAlarm(processor: string, alarm: string, sequenceNumber: number, options: EditAlarmOptions) {
    const body = JSON.stringify(options);
    const url = `${this.yamcs.apiUrl}/processors/${this.instance}/${processor}/alarms${alarm}/${sequenceNumber}`;
    return await this.yamcs.doFetch(url, {
      body,
      method: 'PATCH',
    });
  }

  async getStreams() {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/streams`;
    const response = await this.yamcs.doFetch(url);
    const wrapper = await response.json() as StreamsWrapper;
    return await wrapper.streams || [];
  }

  async getStream(name: string) {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/streams/${name}`;
    const response = await this.yamcs.doFetch(url);
    return await response.json() as Stream;
  }

  async getStreamUpdates(name: string): Promise<StreamSubscriptionResponse> {
    this.prepareWebSocketClient();
    return this.webSocketClient!.getStreamUpdates(name);
  }

  async getTables() {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/tables`;
    const response = await this.yamcs.doFetch(url);
    const wrapper = await response.json() as TablesWrapper;
    return wrapper.tables || [];
  }

  async getTable(name: string) {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/tables/${name}`;
    const response = await this.yamcs.doFetch(url);
    return await response.json() as Table;
  }

  async getTableData(name: string): Promise<Record[]> {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/tables/${name}/data`;
    const response = await this.yamcs.doFetch(url);
    const wrapper = await response.json() as RecordsWrapper;
    return wrapper.record || [];
  }

  async getPacketNames() {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/packet-names`;
    const response = await this.yamcs.doFetch(url);
    const wrapper = await response.json() as PacketNameWrapper;
    return wrapper.name || [];
  }

  async getPacketIndex(options: GetPacketIndexOptions): Promise<IndexGroup[]> {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/packet-index`;
    const response = await this.yamcs.doFetch(url + this.queryString(options));
    const wrapper = await response.json() as IndexResult;
    return wrapper.group || [];
  }

  async getParameterIndex(options: GetParameterIndexOptions): Promise<IndexGroup[]> {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/parameter-index`;
    const response = await this.yamcs.doFetch(url + this.queryString(options));
    const wrapper = await response.json() as IndexResult;
    return wrapper.group || [];
  }

  async getCommandIndex(options: GetCommandIndexOptions): Promise<IndexGroup[]> {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/command-index`;
    const response = await this.yamcs.doFetch(url + this.queryString(options));
    const wrapper = await response.json() as IndexResult;
    return wrapper.group || [];
  }

  async getEventIndex(options: GetEventIndexOptions): Promise<IndexGroup[]> {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/event-index`;
    const response = await this.yamcs.doFetch(url + this.queryString(options));
    const wrapper = await response.json() as IndexResult;
    return wrapper.group || [];
  }

  async getCompletenessIndex(options: GetCompletenessIndexOptions): Promise<IndexGroup[]> {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/completeness-index`;
    const response = await this.yamcs.doFetch(url + this.queryString(options));
    const wrapper = await response.json() as IndexResult;
    return wrapper.group || [];
  }

  getPacketsDownloadURL(options: DownloadPacketsOptions = {}) {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}:exportPackets`;
    return url + this.queryString(options);
  }

  async getRootSpaceSystems() {
    const url = `${this.yamcs.apiUrl}/mdb/${this.instance}`;
    const response = await this.yamcs.doFetch(url);
    const wrapper = await response.json() as SpaceSystemsWrapper;
    return wrapper.spaceSystem || [];
  }

  async getSpaceSystems() {
    const url = `${this.yamcs.apiUrl}/mdb/${this.instance}/space-systems`;
    const response = await this.yamcs.doFetch(url);
    return await response.json() as SpaceSystemsPage;
  }

  async getSpaceSystem(qualifiedName: string) {
    const url = `${this.yamcs.apiUrl}/mdb/${this.instance}/space-systems${qualifiedName}`;
    const response = await this.yamcs.doFetch(url);
    return await response.json() as SpaceSystem;
  }

  async getParameters(options: GetParametersOptions = {}) {
    const url = `${this.yamcs.apiUrl}/mdb/${this.instance}/parameters`;
    const response = await this.yamcs.doFetch(url + this.queryString(options));
    return await response.json() as ParametersPage;
  }

  async getParameter(qualifiedName: string) {
    const url = `${this.yamcs.apiUrl}/mdb/${this.instance}/parameters${qualifiedName}`;
    const response = await this.yamcs.doFetch(url);
    return await response.json() as Parameter;
  }

  async getParameterById(id: NamedObjectId) {
    let url = `${this.yamcs.apiUrl}/mdb/${this.instance}/parameters`;
    if (id.namespace) {
      url += '/' + encodeURIComponent(id.namespace);
      url += '/' + encodeURIComponent(id.name);
      const response = await this.yamcs.doFetch(url);
      return await response.json() as Parameter;
    } else {
      return this.getParameter(id.name);
    }
  }

  async getParameterValues(qualifiedName: string, options: GetParameterValuesOptions = {}): Promise<ParameterValue[]> {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/parameters${qualifiedName}`;
    const response = await this.yamcs.doFetch(url + this.queryString(options));
    const wrapper = await response.json() as ParameterData;
    return wrapper.parameter || [];
  }

  getParameterValuesDownloadURL(options: DownloadParameterValuesOptions = {}) {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}:exportParameterValues`;
    return url + this.queryString(options);
  }

  async getParameterValueUpdates(options: ParameterSubscriptionRequest): Promise<ParameterSubscriptionResponse> {
    this.prepareWebSocketClient();
    return this.webSocketClient!.getParameterValueUpdates(options);
  }

  async unsubscribeParameterValueUpdates(options: ParameterSubscriptionRequest) {
    return this.webSocketClient!.unsubscribeParameterValueUpdates(options);
  }

  async unsubscribeStreamUpdates() {
    return this.webSocketClient!.unsubscribeStreamUpdates();
  }

  async setParameterValue(processorName: string, qualifiedName: string, value: Value) {
    const url = `${this.yamcs.apiUrl}/processors/${this.instance}/${processorName}/parameters${qualifiedName}`;
    return this.yamcs.doFetch(url, {
      body: JSON.stringify(value),
      method: 'PUT',
    });
  }

  async getParameterSamples(qualifiedName: string, options: GetParameterSamplesOptions = {}): Promise<Sample[]> {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/parameters${qualifiedName}/samples`;
    const response = await this.yamcs.doFetch(url + this.queryString(options));
    const wrapper = await response.json() as SamplesWrapper;
    return wrapper.sample || [];
  }

  async getParameterRanges(qualifiedName: string, options: GetParameterRangesOptions = {}): Promise<Range[]> {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/parameters${qualifiedName}/ranges`;
    const response = await this.yamcs.doFetch(url + this.queryString(options));
    const wrapper = await response.json() as RangesWrapper;
    return wrapper.range || [];
  }

  async getCommands(options: GetCommandsOptions = {}) {
    const url = `${this.yamcs.apiUrl}/mdb/${this.instance}/commands`;
    const response = await this.yamcs.doFetch(url + this.queryString(options));
    return await response.json() as CommandsPage;
  }

  async getCommand(qualifiedName: string) {
    const url = `${this.yamcs.apiUrl}/mdb/${this.instance}/commands${qualifiedName}`;
    const response = await this.yamcs.doFetch(url);
    return await response.json() as Command;
  }

  async getContainers(options: GetContainersOptions = {}) {
    const url = `${this.yamcs.apiUrl}/mdb/${this.instance}/containers`;
    const response = await this.yamcs.doFetch(url + this.queryString(options));
    return await response.json() as ContainersPage;
  }

  async getContainer(qualifiedName: string) {
    const url = `${this.yamcs.apiUrl}/mdb/${this.instance}/containers${qualifiedName}`;
    const response = await this.yamcs.doFetch(url);
    return await response.json() as Container;
  }

  async getTags(options: GetTagsOptions = {}) {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/tags`;
    const response = await this.yamcs.doFetch(url + this.queryString(options));
    return await response.json() as TagsPage;
  }

  async getAlgorithms(options: GetAlgorithmsOptions = {}) {
    const url = `${this.yamcs.apiUrl}/mdb/${this.instance}/algorithms`;
    const response = await this.yamcs.doFetch(url + this.queryString(options));
    return await response.json() as AlgorithmsPage;
  }

  async getAlgorithm(qualifiedName: string) {
    const url = `${this.yamcs.apiUrl}/mdb/${this.instance}/algorithms${qualifiedName}`;
    const response = await this.yamcs.doFetch(url);
    return await response.json() as Algorithm;
  }

  async getCfdpTransfers() {
    const url = `${this.yamcs.apiUrl}/cfdp/${this.instance}/transfers`;
    const response = await this.yamcs.doFetch(url);
    return await response.json() as TransfersPage;
  }

  async createCfdpTransfer(options: CreateTransferRequest) {
    const url = `${this.yamcs.apiUrl}/cfdp/${this.instance}/transfers`
    const body = JSON.stringify(options);
    const response = await this.yamcs.doFetch(url, {
      body,
      method: 'POST',
    });
    return await response.json() as Transfer;
  }

  async getGaps() {
    const url = `${this.yamcs.apiUrl}/dass/gaps/${this.instance}`;
    const response = await this.yamcs.doFetch(url)
    return await response.json() as ListGapsResponse;
  }

  async requestPlayback(link: string, options: RequestPlaybackRequest) {
    const url = `${this.yamcs.apiUrl}/dass/links/${this.instance}/${link}:requestPlayback`;
    const body = JSON.stringify(options);
    return await this.yamcs.doFetch(url, {
      body,
      method: 'POST',
    });
  }

  closeConnection() {
    if (this.webSocketClient) {
      this.webSocketClient.close();
      this.webSocketClient = undefined;
    }
  }

  private prepareWebSocketClient() {
    if (!this.webSocketClient) {
      this.webSocketClient = new WebSocketClient(this.yamcs.baseHref, this.instance);
      this.connected$ = this.webSocketClient.connected$;
    }
  }

  private queryString(options: { [key: string]: any }) {
    const qs = Object.keys(options)
      .map(k => `${k}=${options[k]}`)
      .join('&');
    return qs === '' ? qs : '?' + qs;
  }
}
