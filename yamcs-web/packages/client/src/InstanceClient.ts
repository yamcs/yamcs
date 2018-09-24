import { Observable } from 'rxjs';
import { AlarmsWrapper, AlgorithmsWrapper, BucketsWrapper, ClientsWrapper, CommandHistoryEntryWrapper, CommandQueuesWrapper, CommandsWrapper, ContainersWrapper, EventsWrapper, IndexResult, LinksWrapper, PacketNameWrapper, ParametersWrapper, ProcessorsWrapper, RangesWrapper, RecordsWrapper, SamplesWrapper, ServicesWrapper, SourcesWrapper, SpaceSystemsWrapper, StreamsWrapper, TablesWrapper } from './types/internal';
import { Algorithm, Command, Container, GetAlgorithmsOptions, GetCommandsOptions, GetContainersOptions, GetParametersOptions, NamedObjectId, Parameter, SpaceSystem } from './types/mdb';
import { Alarm, AlarmSubscriptionResponse, BatchDownloadParameterValuesOptions, CommandHistoryEntry, CreateEventRequest, CreateProcessorRequest, DownloadEventsOptions, DownloadPacketsOptions, DownloadParameterValuesOptions, EditReplayProcessorRequest, Event, EventSubscriptionResponse, GetAlarmsOptions, GetCommandHistoryOptions, GetEventsOptions, GetPacketIndexOptions, GetParameterRangesOptions, GetParameterSamplesOptions, GetParameterValuesOptions, IndexGroup, IssueCommandOptions, IssueCommandResponse, ParameterData, ParameterSubscriptionRequest, ParameterSubscriptionResponse, ParameterValue, Range, Sample, TimeSubscriptionResponse, Value } from './types/monitoring';
import { Bucket, ClientInfo, ClientSubscriptionResponse, CommandQueue, CommandQueueEventSubscriptionResponse, CommandQueueSubscriptionResponse, ConnectionInfoSubscriptionResponse, CreateBucketRequest, EditCommandQueueEntryOptions, EditCommandQueueOptions, InstanceSubscriptionResponse, Link, LinkSubscriptionResponse, ListObjectsOptions, ListObjectsResponse, Processor, ProcessorSubscriptionResponse, Record, Service, StatisticsSubscriptionResponse, Stream, Table } from './types/system';
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

  async getEvents(options: GetEventsOptions = {}) {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/events`;
    const response = await this.yamcs.doFetch(url + this.queryString(options));
    const wrapper = await response.json() as EventsWrapper
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
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/downloads/events`;
    return url + this.queryString(options);
  }

  async getEventUpdates(): Promise<EventSubscriptionResponse> {
    this.prepareWebSocketClient();
    return this.webSocketClient!.getEventUpdates();
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

  async getLinks(): Promise<Link[]> {
    const url = `${this.yamcs.apiUrl}/links/${this.instance}`
    const response = await this.yamcs.doFetch(url);
    const wrapper = await response.json() as LinksWrapper;
    return wrapper.link || [];
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

  async enableLink(name: string) {
    const body = JSON.stringify({
      state: 'enabled',
    })
    return this.yamcs.doFetch(`${this.yamcs.apiUrl}/links/${this.instance}/${name}`, {
      body,
      method: 'PATCH',
    });
  }

  async disableLink(name: string) {
    const body = JSON.stringify({
      state: 'disabled',
    })
    return this.yamcs.doFetch(`${this.yamcs.apiUrl}/links/${this.instance}/${name}`, {
      body,
      method: 'PATCH',
    });
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

  async issueCommand(processorName: string, qualifiedName: string, options?: IssueCommandOptions): Promise<IssueCommandResponse> {
    const body = JSON.stringify(options);
    const response = await this.yamcs.doFetch(`${this.yamcs.apiUrl}/processors/${this.instance}/${processorName}/commands${qualifiedName}`, {
      body,
      method: 'POST',
    });
    return await response.json() as IssueCommandResponse;
  }

  async getCommandHistoryEntries(options: GetCommandHistoryOptions = {}): Promise<CommandHistoryEntry[]> {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/commands`;
    const response = await this.yamcs.doFetch(url + this.queryString(options));
    const wrapper = await response.json() as CommandHistoryEntryWrapper;
    return wrapper.entry || [];
  }

  async getCommandHistoryEntriesForParameter(qualifiedName: string, options: GetCommandHistoryOptions = {}) {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/commands${qualifiedName}`;
    const response = await this.yamcs.doFetch(url + this.queryString(options));
    const wrapper = await response.json() as CommandHistoryEntryWrapper;
    return wrapper.entry || [];
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
      method: 'POST',
    });
    return await response.json() as CommandQueue;
  }

  async getCommandQueueEventUpdates(): Promise<CommandQueueEventSubscriptionResponse> {
    this.prepareWebSocketClient();
    return this.webSocketClient!.getCommandQueueEventUpdates(this.instance);
  }

  async editCommandQueueEntry(processorName: string, queueName: string, uuid: string, options: EditCommandQueueEntryOptions) {
    const url = `${this.yamcs.apiUrl}/processors/${this.instance}/${processorName}/cqueues/${queueName}/entries/${uuid}`;
    const body = JSON.stringify(options);
    const response = await this.yamcs.doFetch(url, {
      body,
      method: 'POST',
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
    return wrapper.service || [];
  }

  async getService(name: string): Promise<Service> {
    const url = `${this.yamcs.apiUrl}/services/${this.instance}/${name}`;
    const response = await this.yamcs.doFetch(url);
    return await response.json() as Service;
  }

  async startService(name: string) {
    const body = JSON.stringify({
      state: 'running'
    })
    return this.yamcs.doFetch(`${this.yamcs.apiUrl}/services/${this.instance}/${name}`, {
      body,
      method: 'PATCH',
    });
  }

  async stopService(name: string) {
    const body = JSON.stringify({
      state: 'stopped'
    })
    return this.yamcs.doFetch(`${this.yamcs.apiUrl}/services/${this.instance}/${name}`, {
      body,
      method: 'PATCH',
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
    return this.webSocketClient!.getAlarmUpdates();
  }

  async getAlarmsForParameter(qualifiedName: string, options: GetAlarmsOptions = {}) {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/alarms${qualifiedName}`;
    const response = await this.yamcs.doFetch(url + this.queryString(options));
    const wrapper = await response.json() as AlarmsWrapper;
    return await wrapper.alarm || [];
  }

  async getStreams() {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/streams`;
    const response = await this.yamcs.doFetch(url);
    const wrapper = await response.json() as StreamsWrapper;
    return await wrapper.stream || [];
  }

  async getStream(name: string) {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/streams/${name}`;
    const response = await this.yamcs.doFetch(url);
    return await response.json() as Stream;
  }

  async getTables() {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/tables`;
    const response = await this.yamcs.doFetch(url);
    const wrapper = await response.json() as TablesWrapper;
    return wrapper.table || [];
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

  getPacketsDownloadURL(options: DownloadPacketsOptions = {}) {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/downloads/packets`;
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
    const wrapper = await response.json() as SpaceSystemsWrapper;
    return wrapper.spaceSystem || [];
  }

  async getSpaceSystem(qualifiedName: string) {
    const url = `${this.yamcs.apiUrl}/mdb/${this.instance}/space-systems${qualifiedName}`;
    const response = await this.yamcs.doFetch(url);
    return await response.json() as SpaceSystem;
  }

  async getParameters(options: GetParametersOptions = {}) {
    const url = `${this.yamcs.apiUrl}/mdb/${this.instance}/parameters`;
    const response = await this.yamcs.doFetch(url + this.queryString(options));
    const wrapper = await response.json() as ParametersWrapper;
    return wrapper.parameter || [];
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

  getParameterValuesDownloadURL(qualifiedName: string, options: DownloadParameterValuesOptions = {}) {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/downloads/parameters${qualifiedName}`;
    return url + this.queryString(options);
  }

  getBatchParameterValuesDownloadURL(options: BatchDownloadParameterValuesOptions = {}) {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/downloads/parameters`;
    return url + this.queryString(options);
  }

  async getParameterValueUpdates(options: ParameterSubscriptionRequest): Promise<ParameterSubscriptionResponse> {
    this.prepareWebSocketClient();
    return this.webSocketClient!.getParameterValueUpdates(options);
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
    const wrapper = await response.json() as CommandsWrapper;
    return wrapper.command || []
  }

  async getCommand(qualifiedName: string) {
    const url = `${this.yamcs.apiUrl}/mdb/${this.instance}/commands${qualifiedName}`;
    const response = await this.yamcs.doFetch(url);
    return await response.json() as Command;
  }

  async getContainers(options: GetContainersOptions = {}) {
    const url = `${this.yamcs.apiUrl}/mdb/${this.instance}/containers`;
    const response = await this.yamcs.doFetch(url + this.queryString(options));
    const wrapper = await response.json() as ContainersWrapper;
    return wrapper.container || [];
  }

  async getContainer(qualifiedName: string) {
    const url = `${this.yamcs.apiUrl}/mdb/${this.instance}/containers${qualifiedName}`;
    const response = await this.yamcs.doFetch(url);
    return await response.json() as Container;
  }

  async getAlgorithms(options: GetAlgorithmsOptions = {}) {
    const url = `${this.yamcs.apiUrl}/mdb/${this.instance}/algorithms`;
    const response = await this.yamcs.doFetch(url + this.queryString(options));
    const wrapper = await response.json() as AlgorithmsWrapper;
    return wrapper.algorithm || [];
  }

  async getAlgorithm(qualifiedName: string) {
    const url = `${this.yamcs.apiUrl}/mdb/${this.instance}/algorithms${qualifiedName}`;
    const response = await this.yamcs.doFetch(url);
    return await response.json() as Algorithm;
  }

  async createBucket(options: CreateBucketRequest) {
    const body = JSON.stringify(options);
    const response = await this.yamcs.doFetch(`${this.yamcs.apiUrl}/buckets/${this.instance}`, {
      body,
      method: 'POST',
    });
    return await response.json() as Event;
  }

  async getBuckets(): Promise<Bucket[]> {
    const response = await this.yamcs.doFetch(`${this.yamcs.apiUrl}/buckets/${this.instance}`);
    const wrapper = await response.json() as BucketsWrapper;
    return wrapper.bucket || [];
  }

  async deleteBucket(name: string) {
    const url = `${this.yamcs.apiUrl}/buckets/${this.instance}/${name}`;
    return await this.yamcs.doFetch(url, {
      method: 'DELETE',
    });
  }

  async listObjects(bucket: string, options: ListObjectsOptions = {}): Promise<ListObjectsResponse> {
    const url = `${this.yamcs.apiUrl}/buckets/${this.instance}/${bucket}`;
    const response = await this.yamcs.doFetch(url + this.queryString(options));
    return await response.json() as ListObjectsResponse;
  }

  async getObject(bucket: string, name: string) {
    return await this.yamcs.doFetch(this.getObjectURL(bucket, name));
  }

  getObjectURL(bucket: string, name: string) {
    return `${this.yamcs.apiUrl}/buckets/${this.instance}/${bucket}/${name}`;
  }

  async uploadObject(bucket: string, name: string, value: Blob) {
    const url = `${this.yamcs.apiUrl}/buckets/${this.instance}/${bucket}`;
    const formData = new FormData();
    formData.set(name, value, name);
    return await this.yamcs.doFetch(url, {
      method: 'POST',
      body: formData,
    });
  }

  async deleteObject(bucket: string, name: string) {
    const url = `${this.yamcs.apiUrl}/buckets/${this.instance}/${bucket}/${name}`;
    return await this.yamcs.doFetch(url, {
      method: 'DELETE',
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
      this.webSocketClient = new WebSocketClient(this.instance);
      this.connected$ = this.webSocketClient.connected$;
    }
  }

  private queryString(options: {[key: string]: any}) {
    const qs = Object.keys(options)
      .map(k => `${k}=${options[k]}`)
      .join('&');
    return qs === '' ? qs : '?' + qs;
  }
}
