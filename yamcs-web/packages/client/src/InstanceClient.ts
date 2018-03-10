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
  GetEventsOptions,
  GetParametersOptions,
  MissionDatabase,
  Parameter,
  SpaceSystem,
  GetParameterSamplesOptions,
} from './types/mdb';

import {
  Alarm,
  DisplayFolder,
  Event,
  ParameterData,
  ParameterSubscriptionRequest,
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

  getEvents(options: GetEventsOptions = {}) {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/events`;
    const queryString = this.queryString(options);
    return fetch(url + queryString)
      .then(res => res.json() as Promise<EventsWrapper>)
      .then(wrapper => wrapper.event || []);
  }

  getEventUpdates() {
    this.prepareWebSocketClient();
    return this.webSocketClient.getEventUpdates();
  }

  getLinks() {
    return fetch(`${this.yamcs.apiUrl}/links/${this.instance}`)
      .then(res => res.json() as Promise<LinksWrapper>)
      .then(wrapper => wrapper.link || []);
  }

  getLinkUpdates() {
    this.prepareWebSocketClient();
    return this.webSocketClient.getLinkUpdates().pipe(
      filter(msg => msg.linkInfo.instance === this.instance)
    );
  }

  enableLink(name: string) {
    const body = JSON.stringify({
      state: 'enabled',
    })
    return fetch(`${this.yamcs.apiUrl}/links/${this.instance}/${name}`, {
      body,
      method: 'PATCH',
    });
  }

  disableLink(name: string) {
    const body = JSON.stringify({
      state: 'disabled',
    })
    return fetch(`${this.yamcs.apiUrl}/links/${this.instance}/${name}`, {
      body,
      method: 'PATCH',
    });
  }

  getProcessors() {
    return fetch(`${this.yamcs.apiUrl}/processors/${this.instance}`)  .then(res => res.json() as Promise<ProcessorsWrapper>)
      .then(wrapper => wrapper.processor || []);
  }

  getProcessor(name: string) {
    return fetch(`${this.yamcs.apiUrl}/processors/${this.instance}/${name}`)
      .then(res => res.json() as Promise<Processor>);
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

  getCommandQueues(processorName: string) {
    const url = `${this.yamcs.apiUrl}/processors/${this.instance}/${processorName}/cqueues`;
    return fetch(url)
      .then(res => res.json() as Promise<CommandQueuesWrapper>)
      .then(wrapper => wrapper.queue);
  }

  getCommandQueue(processorName: string, queueName: string) {
    const url = `${this.yamcs.apiUrl}/processors/${this.instance}/${processorName}/cqueues/${queueName}`;
    return fetch(url)
      .then(res => res.json());
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

  getClients() {
    return fetch(`${this.yamcs.apiUrl}/instances/${this.instance}/clients`)
      .then(res => res.json() as Promise<ClientsWrapper>)
      .then(wrapper => wrapper.client || []);
  }

  getClient(id: number) {
    return fetch(`${this.yamcs.apiUrl}/clients/${id}`)
      .then(res => res.json() as Promise<ClientInfo>);
  }

  getClientUpdates() {
    this.prepareWebSocketClient();
    return this.webSocketClient.getClientUpdates().pipe(
      filter(msg => msg.instance === this.instance)
    );
  }

  getServices() {
    return fetch(`${this.yamcs.apiUrl}/services/${this.instance}`)
      .then(res => res.json() as Promise<ServicesWrapper>)
      .then(wrapper => wrapper.service || []);
  }

  startService(name: string) {
    const body = JSON.stringify({
      state: 'running'
    })
    return fetch(`${this.yamcs.apiUrl}/services/${this.instance}/service/${name}`, {
      body,
      method: 'PATCH',
    });
  }

  stopService(name: string) {
    const body = JSON.stringify({
      state: 'stopped'
    })
    return fetch(`${this.yamcs.apiUrl}/services/${this.instance}/service/${name}`, {
      body,
      method: 'PATCH',
    });
  }

  getActiveAlarms(processorName: string) {
    const url = `${this.yamcs.apiUrl}/processors/${this.instance}/${processorName}/alarms`;
    return fetch(url)
      .then(res => res.json() as Promise<AlarmsWrapper>)
      .then(wrapper => wrapper.alarm || []);
  }

  getAlarms() {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/alarms`;
    return fetch(url)
      .then(res => res.json() as Promise<AlarmsWrapper>)
      .then(wrapper => wrapper.alarm || []);
  }

  getAlarmUpdates() {
    this.prepareWebSocketClient();
    return this.webSocketClient.getAlarmUpdates();
  }

  getAlarmsForParameter(qualifiedName: string) {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/alarms${qualifiedName}`;
    return fetch(url)
      .then(res => res.json() as Promise<AlarmsWrapper>)
      .then(wrapper => wrapper.alarm || []);
  }

  getStreams() {
    return fetch(`${this.yamcs.apiUrl}/archive/${this.instance}/streams`)
      .then(res => res.json() as Promise<StreamsWrapper>)
      .then(wrapper => wrapper.stream || []);
  }

  getStream(name: string) {
    return fetch(`${this.yamcs.apiUrl}/archive/${this.instance}/streams/${name}`)
      .then(res => res.json() as Promise<Stream>);
  }

  getTables() {
    return fetch(`${this.yamcs.apiUrl}/archive/${this.instance}/tables`)
      .then(res => res.json() as Promise<TablesWrapper>)
      .then(wrapper => wrapper.table || []);
  }

  getTable(name: string) {
    return fetch(`${this.yamcs.apiUrl}/archive/${this.instance}/tables/${name}`)
      .then(res => res.json() as Promise<Table>);
  }

  getTableData(name: string) {
    return fetch(`${this.yamcs.apiUrl}/archive/${this.instance}/tables/${name}/data`)
      .then(res => res.json() as Promise<RecordsWrapper>)
      .then(wrapper => wrapper.record || []);
  }

  getRootSpaceSystems() {
    return fetch(`${this.yamcs.apiUrl}/mdb/${this.instance}`)
      .then(res => res.json() as Promise<SpaceSystemsWrapper>)
      .then(wrapper => wrapper.spaceSystem || []);
  }

  getSpaceSystems() {
    return fetch(`${this.yamcs.apiUrl}/mdb/${this.instance}/space-systems`)
      .then(res => res.json() as Promise<SpaceSystemsWrapper>)
      .then(wrapper => wrapper.spaceSystem || []);
  }

  getSpaceSystem(qualifiedName: string) {
    return fetch(`${this.yamcs.apiUrl}/mdb/${this.instance}/space-systems${qualifiedName}`)
      .then(res => res.json() as Promise<SpaceSystem>);
  }

  getParameters(options: GetParametersOptions = {}) {
    const url = `${this.yamcs.apiUrl}/mdb/${this.instance}/parameters`;
    const queryString = this.queryString(options);
    return fetch(url + queryString)
      .then(res => res.json() as Promise<ParametersWrapper>)
      .then(wrapper => wrapper.parameter);
  }

  getParameter(qualifiedName: string) {
    return fetch(`${this.yamcs.apiUrl}/mdb/${this.instance}/parameters${qualifiedName}`)
      .then(res => res.json() as Promise<Parameter>);
  }

  getParameterValueUpdates(options: ParameterSubscriptionRequest) {
    this.prepareWebSocketClient();
    return this.webSocketClient.getParameterValueUpdates(options);
  }

  getParameterSamples(qualifiedName: string, options: GetParameterSamplesOptions = {}) {
    const url = `${this.yamcs.apiUrl}/archive/${this.instance}/parameters${qualifiedName}/samples`;
    const queryString = this.queryString(options);
    return fetch(url + queryString)
      .then(res => res.json() as Promise<SamplesWrapper>)
      .then(wrapper => wrapper.sample || []);
  }

  getCommands(options: GetCommandsOptions = {}) {
    const url = `${this.yamcs.apiUrl}/mdb/${this.instance}/commands`;
    const queryString = this.queryString(options);
    return fetch(url + queryString)
      .then(res => res.json() as Promise<CommandsWrapper>)
      .then(wrapper => wrapper.command || []);
  }

  getCommand(qualifiedName: string) {
    return fetch(`${this.yamcs.apiUrl}/mdb/${this.instance}/commands${qualifiedName}`)
      .then(res => res.json() as Promise<Command>);
  }

  getContainers(options: GetContainersOptions = {}) {
    const url = `${this.yamcs.apiUrl}/mdb/${this.instance}/containers`;
    const queryString = this.queryString(options);
    return fetch(url + queryString)
      .then(res => res.json() as Promise<ContainersWrapper>)
      .then(wrapper => wrapper.container || []);
  }

  getContainer(qualifiedName: string) {
    return fetch(`${this.yamcs.apiUrl}/mdb/${this.instance}/containers${qualifiedName}`)
      .then(res => res.json() as Promise<Container>);
  }

  getAlgorithms(options: GetAlgorithmsOptions = {}) {
    const url = `${this.yamcs.apiUrl}/mdb/${this.instance}/algorithms`;
    const queryString = this.queryString(options);
    return fetch(url + queryString)
      .then(res => res.json() as Promise<AlgorithmsWrapper>)
      .then(wrapper => wrapper.algorithm);
  }

  getAlgorithm(qualifiedName: string) {
    return fetch(`${this.yamcs.apiUrl}/mdb/${this.instance}/algorithms${qualifiedName}`)
      .then(res => res.json() as Promise<Algorithm>);
  }

  getDisplayInfo() {
    return fetch(`${this.yamcs.apiUrl}/displays/${this.instance}`)
      .then(res => res.json() as Promise<DisplayFolder>);
  }

  /**
   * Returns a string representation of the display definition file
   */
  getDisplay(path: string) {
    return fetch(`${this.yamcs.staticUrl}/${this.instance}/displays${path}`)
      .then(res => res.text());
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
