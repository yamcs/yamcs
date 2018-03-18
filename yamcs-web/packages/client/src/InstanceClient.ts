import { catchError, filter, map } from 'rxjs/operators';
import { WebSocketClient } from './WebSocketClient';

import YamcsClient from './YamcsClient';

import {
  AlarmsWrapper,
  AlgorithmsWrapper,
  CommandsWrapper,
  CommandQueuesWrapper,
  ContainersWrapper,
  EventsWrapper,
  LinksWrapper,
  ParametersWrapper,
  ProcessorsWrapper,
  RecordsWrapper,
  ServicesWrapper,
  SpaceSystemsWrapper,
  StreamsWrapper,
  TablesWrapper,
  ClientsWrapper,
  SamplesWrapper,
} from './types/internal';

import {
  Algorithm,
  Command,
  Container,
  GetAlgorithmsOptions,
  GetCommandsOptions,
  GetContainersOptions,
  GetParametersOptions,
  MissionDatabase,
  Parameter,
  SpaceSystem,
  GetParameterSamplesOptions,
} from './types/mdb';

import {
  Alarm,
  DisplayFolder,
  DownloadEventsOptions,
  DownloadParameterValuesOptions,
  Event,
  GetEventsOptions,
  GetParameterValuesOptions,
  ParameterData,
  ParameterSubscriptionRequest,
  ParameterSubscriptionResponse,
  ParameterValue,
  Sample,
  TimeInfo,
} from './types/monitoring';

import {
  ClientInfo,
  CommandQueueEvent,
  Link,
  LinkEvent,
  Processor,
  Record,
  Service,
  Statistics,
  Stream,
  Table,
  CommandQueue,
} from './types/system';
import { Observable } from 'rxjs/Observable';

export class InstanceClient {

  public connected$: Observable<boolean>;
  private webSocketClient: WebSocketClient;

  constructor(
    readonly instance: string,
    private yamcs: YamcsClient) {
  }

  getTimeUpdates() {
    this.prepareWebSocketClient();
    return this.webSocketClient.getTimeUpdates();
  }

  async getEvents(options: GetEventsOptions = {}) {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/events`;
    const response = await fetch(url + this.queryString(options));
    const wrapper = await response.json() as EventsWrapper
    return wrapper.event || [];
  }

  getEventsDownloadURL(options: DownloadEventsOptions = {}) {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/downloads/events`;
    return url + this.queryString(options);
  }

  getEventUpdates() {
    this.prepareWebSocketClient();
    return this.webSocketClient.getEventUpdates();
  }

  async getLinks() {
    const url = `${this.yamcs.apiUrl}/links/${this.instance}`
    const response = await fetch(url);
    const wrapper = await response.json() as LinksWrapper;
    return wrapper.link || [];
  }

  getLinkUpdates() {
    this.prepareWebSocketClient();
    return this.webSocketClient.getLinkUpdates().pipe(
      filter(msg => msg.linkInfo.instance === this.instance)
    );
  }

  async enableLink(name: string) {
    const body = JSON.stringify({
      state: 'enabled',
    })
    return fetch(`${this.yamcs.apiUrl}/links/${this.instance}/${name}`, {
      body,
      method: 'PATCH',
    });
  }

  async disableLink(name: string) {
    const body = JSON.stringify({
      state: 'disabled',
    })
    return fetch(`${this.yamcs.apiUrl}/links/${this.instance}/${name}`, {
      body,
      method: 'PATCH',
    });
  }

  async getProcessors() {
    const url = `${this.yamcs.apiUrl}/processors/${this.instance}`;
    const response = await fetch(url);
    const wrapper = await response.json() as ProcessorsWrapper;
    return wrapper.processor || [];
  }

  async getProcessor(name: string) {
    const url = `${this.yamcs.apiUrl}/processors/${this.instance}/${name}`;
    const response = await fetch(url);
    return await response.json() as Processor;
  }

  getProcessorUpdates() {
    this.prepareWebSocketClient();
    return this.webSocketClient.getProcessorUpdates().pipe(
      filter(msg => msg.instance === this.instance)
    );
  }

  getProcessorStatistics() {
    this.prepareWebSocketClient();
    return this.webSocketClient.getProcessorStatistics().pipe(
      filter(msg => msg.instance === this.instance)
    );
  }

  async getCommandQueues(processorName: string) {
    const url = `${this.yamcs.apiUrl}/processors/${this.instance}/${processorName}/cqueues`;
    const response = await fetch(url);
    const wrapper = await response.json() as CommandQueuesWrapper;
    return wrapper.queue;
  }

  async getCommandQueue(processorName: string, queueName: string) {
    const url = `${this.yamcs.apiUrl}/processors/${this.instance}/${processorName}/cqueues/${queueName}`;
    const response = await fetch(url);
    return await response.json() as CommandQueue;
  }

  getCommandQueueUpdates(processorName?: string) {
    this.prepareWebSocketClient();
    const cqueues$ = this.webSocketClient.getCommandQueueUpdates().pipe(
      filter(msg => msg.instance === this.instance)
    );
    if (processorName === undefined) {
      return cqueues$;
    } else {
      return cqueues$.pipe(
        filter(msg => msg.processorName === processorName)
      );
    }
  }

  getCommandQueueEventUpdates() {
    this.prepareWebSocketClient();
    return this.webSocketClient.getCommandQueueEventUpdates().pipe(
      filter(msg => msg.data.instance === this.instance)
    );
  }

  async getClients() {
    const url = `${this.yamcs.apiUrl}/instances/${this.instance}/clients`;
    const response = await fetch(url);
    const wrapper = await response.json() as ClientsWrapper;
    return wrapper.client || [];
  }

  async getClient(id: number) {
    const url = `${this.yamcs.apiUrl}/clients/${id}`;
    const response = await fetch(url);
    return await response.json() as ClientInfo;
  }

  getClientUpdates() {
    this.prepareWebSocketClient();
    return this.webSocketClient.getClientUpdates().pipe(
      filter(msg => msg.instance === this.instance)
    );
  }

  async getServices() {
    const url = `${this.yamcs.apiUrl}/services/${this.instance}`;
    const response = await fetch(url);
    const wrapper = await response.json() as ServicesWrapper;
    return wrapper.service || [];
  }

  async startService(name: string) {
    const body = JSON.stringify({
      state: 'running'
    })
    return fetch(`${this.yamcs.apiUrl}/services/${this.instance}/service/${name}`, {
      body,
      method: 'PATCH',
    });
  }

  async stopService(name: string) {
    const body = JSON.stringify({
      state: 'stopped'
    })
    return fetch(`${this.yamcs.apiUrl}/services/${this.instance}/service/${name}`, {
      body,
      method: 'PATCH',
    });
  }

  async getActiveAlarms(processorName: string) {
    const url = `${this.yamcs.apiUrl}/processors/${this.instance}/${processorName}/alarms`;
    const response = await fetch(url);
    const wrapper = await response.json() as AlarmsWrapper;
    return wrapper.alarm || [];
  }

  async getAlarms() {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/alarms`;
    const response = await fetch(url);
    const wrapper = await response.json() as AlarmsWrapper;
    return wrapper.alarm || [];
  }

  getAlarmUpdates() {
    this.prepareWebSocketClient();
    return this.webSocketClient.getAlarmUpdates();
  }

  async getAlarmsForParameter(qualifiedName: string) {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/alarms${qualifiedName}`;
    const response = await fetch(url);
    const wrapper = await response.json() as AlarmsWrapper;
    return await wrapper.alarm || [];
  }

  async getStreams() {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/streams`;
    const response = await fetch(url);
    const wrapper = await response.json() as StreamsWrapper;
    return await wrapper.stream || [];
  }

  async getStream(name: string) {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/streams/${name}`;
    const response = await fetch(url);
    return await response.json() as Stream;
  }

  async getTables() {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/tables`;
    const response = await fetch(url);
    const wrapper = await response.json() as TablesWrapper;
    return wrapper.table || [];
  }

  async getTable(name: string) {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/tables/${name}`;
    const response = await fetch(url);
    return await response.json() as Table;
  }

  async getTableData(name: string) {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/tables/${name}/data`;
    const response = await fetch(url);
    const wrapper = await response.json() as RecordsWrapper;
    return wrapper.record || [];
  }

  async getRootSpaceSystems() {
    const url = `${this.yamcs.apiUrl}/mdb/${this.instance}`;
    const response = await fetch(url);
    const wrapper = await response.json() as SpaceSystemsWrapper;
    return wrapper.spaceSystem || [];
  }

  async getSpaceSystems() {
    const url = `${this.yamcs.apiUrl}/mdb/${this.instance}/space-systems`;
    const response = await fetch(url);
    const wrapper = await response.json() as SpaceSystemsWrapper;
    return wrapper.spaceSystem || [];
  }

  async getSpaceSystem(qualifiedName: string) {
    const url = `${this.yamcs.apiUrl}/mdb/${this.instance}/space-systems${qualifiedName}`;
    const response = await fetch(url);
    return await response.json() as SpaceSystem;
  }

  async getParameters(options: GetParametersOptions = {}) {
    const url = `${this.yamcs.apiUrl}/mdb/${this.instance}/parameters`;
    const response = await fetch(url + this.queryString(options));
    const wrapper = await response.json() as ParametersWrapper;
    return wrapper.parameter || [];
  }

  async getParameter(qualifiedName: string) {
    const url = `${this.yamcs.apiUrl}/mdb/${this.instance}/parameters${qualifiedName}`;
    const response = await fetch(url);
    return await response.json() as Parameter;
  }

  async getParameterValues(qualifiedName: string, options: GetParameterValuesOptions = {}): Promise<ParameterValue[]> {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/parameters${qualifiedName}`;
    const response = await fetch(url + this.queryString(options));
    const wrapper = await response.json() as ParameterData;
    return wrapper.parameter || [];
  }

  getParameterValuesDownloadURL(qualifiedName: string, options: DownloadParameterValuesOptions = {}) {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/downloads/parameters${qualifiedName}`;
    return url + this.queryString(options);
  }

  async getParameterValueUpdates(options: ParameterSubscriptionRequest): Promise<ParameterSubscriptionResponse> {
    this.prepareWebSocketClient();
    return this.webSocketClient.getParameterValueUpdates(options);
  }

  async getParameterSamples(qualifiedName: string, options: GetParameterSamplesOptions = {}) {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/parameters${qualifiedName}/samples`;
    const response = await fetch(url + this.queryString(options));
    const wrapper = await response.json() as SamplesWrapper;
    return wrapper.sample || [];
  }

  async getCommands(options: GetCommandsOptions = {}) {
    const url = `${this.yamcs.apiUrl}/mdb/${this.instance}/commands`;
    const response = await fetch(url + this.queryString(options));
    const wrapper = await response.json() as CommandsWrapper;
    return wrapper.command || []
  }

  async getCommand(qualifiedName: string) {
    const url = `${this.yamcs.apiUrl}/mdb/${this.instance}/commands${qualifiedName}`;
    const response = await fetch(url);
    return await response.json() as Command;
  }

  async getContainers(options: GetContainersOptions = {}) {
    const url = `${this.yamcs.apiUrl}/mdb/${this.instance}/containers`;
    const response = await fetch(url + this.queryString(options));
    const wrapper = await response.json() as ContainersWrapper;
    return wrapper.container || [];
  }

  async getContainer(qualifiedName: string) {
    const url = `${this.yamcs.apiUrl}/mdb/${this.instance}/containers${qualifiedName}`;
    const response = await fetch(url);
    return await response.json() as Container;
  }

  async getAlgorithms(options: GetAlgorithmsOptions = {}) {
    const url = `${this.yamcs.apiUrl}/mdb/${this.instance}/algorithms`;
    const response = await fetch(url + this.queryString(options));
    const wrapper = await response.json() as AlgorithmsWrapper;
    return wrapper.algorithm || [];
  }

  async getAlgorithm(qualifiedName: string) {
    const url = `${this.yamcs.apiUrl}/mdb/${this.instance}/algorithms${qualifiedName}`;
    const response = await fetch(url);
    return await response.json() as Algorithm;
  }

  async getDisplayInfo() {
    const response = await fetch(`${this.yamcs.apiUrl}/displays/${this.instance}`)
    return await response.json() as DisplayFolder;
  }

  /**
   * Returns a string representation of the display definition file
   */
  async getDisplay(path: string) {
    const url = `${this.yamcs.staticUrl}/${this.instance}/displays${path}`;
    const response = await fetch(url);
    return await response.text();
  }

  closeConnection() {
    if (this.webSocketClient) {
      this.webSocketClient.close();
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
