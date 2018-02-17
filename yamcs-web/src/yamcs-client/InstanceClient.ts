import { HttpClient, HttpParams } from '@angular/common/http';
import { catchError, filter, map } from 'rxjs/operators';
import { WebSocketClient } from './WebSocketClient';

import YamcsClient from './YamcsClient';

import {
  AlgorithmsWrapper,
  CommandsWrapper,
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
} from './types/mdb';

import {
  DisplayFolder,
  Event,
  ParameterSubscriptionRequest,
} from './types/monitoring';

import {
  ClientInfo,
  Link,
  Processor,
  Record,
  Service,
  Stream,
  Table,
} from './types/system';

export class InstanceClient {

  private webSocketClient: WebSocketClient;

  constructor(
    readonly instance: string,
    private yamcs: YamcsClient,
    private http: HttpClient) {
  }

  getTimeUpdates() {
    this.prepareWebSocketClient();
    return this.webSocketClient.getTimeUpdates();
  }

  getEvents() {
    return this.http.get<EventsWrapper>(`${this.yamcs.apiUrl}/archive/${this.instance}/events`).pipe(
      map(msg => msg.event || []),
      catchError(this.yamcs.handleError<Event[]>([]))
    );
  }

  getEventUpdates() {
    this.prepareWebSocketClient();
    return this.webSocketClient.getEventUpdates();
  }

  getLinks() {
    return this.http.get<LinksWrapper>(`${this.yamcs.apiUrl}/links/${this.instance}`).pipe(
      map(msg => msg.link || []),
      catchError(this.yamcs.handleError<Link[]>([]))
    );
  }

  getLinkUpdates() {
    this.prepareWebSocketClient();
    return this.webSocketClient.getLinkUpdates();
  }

  enableLink(name: string) {
    return this.http.patch(`${this.yamcs.apiUrl}/links/${this.instance}/${name}`, {
      state: 'enabled'
    });
  }

  disableLink(name: string) {
    return this.http.patch(`${this.yamcs.apiUrl}/links/${this.instance}/${name}`, {
      state: 'disabled'
    });
  }

  getProcessors() {
    return this.http.get<ProcessorsWrapper>(`${this.yamcs.apiUrl}/processors/${this.instance}`).pipe(
      map(msg => msg.processor || []),
      catchError(this.yamcs.handleError<Processor[]>([]))
    );
  }

  getProcessor(name: string) {
    return this.http.get<Processor>(`${this.yamcs.apiUrl}/processors/${this.instance}/${name}`).pipe(
      catchError(this.yamcs.handleError<Processor>())
    );
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

  getCommandQueueInfoUpdates() {
    this.prepareWebSocketClient();
    return this.webSocketClient.getCommandQueueInfoUpdates().pipe(
      filter(msg => msg.instance === this.instance)
    );
  }

  getCommandQueueEventUpdates() {
    this.prepareWebSocketClient();
    return this.webSocketClient.getCommandQueueEventUpdates().pipe(
      filter(msg => msg.data.instance === this.instance)
    );
  }

  getClients() {
    return this.http.get<ClientsWrapper>(`${this.yamcs.apiUrl}/instances/${this.instance}/clients`).pipe(
      map(msg => msg.client || []),
      catchError(this.yamcs.handleError<ClientInfo[]>([]))
    );
  }

  getClient(id: number) {
    return this.http.get<ClientInfo>(`${this.yamcs.apiUrl}/clients/${id}`).pipe(
      catchError(this.yamcs.handleError<ClientInfo>())
    );
  }

  getClientUpdates() {
    this.prepareWebSocketClient();
    return this.webSocketClient.getClientUpdates().pipe(
      filter(msg => msg.instance === this.instance)
    );
  }

  getServices() {
    return this.http.get<ServicesWrapper>(`${this.yamcs.apiUrl}/services/${this.instance}`).pipe(
      map(msg => msg.service || []),
      catchError(this.yamcs.handleError<Service[]>([]))
    );
  }

  startService(name: string) {
    return this.http.patch(`${this.yamcs.apiUrl}/services/${this.instance}/service/${name}`, {
      state: 'running'
    });
  }

  stopService(name: string) {
    return this.http.patch(`${this.yamcs.apiUrl}/services/${this.instance}/service/${name}`, {
      state: 'stopped'
    });
  }

  getStreams() {
    return this.http.get<StreamsWrapper>(`${this.yamcs.apiUrl}/archive/${this.instance}/streams`).pipe(
      map(msg => msg.stream || []),
      catchError(this.yamcs.handleError<Stream[]>([]))
    );
  }

  getStream(name: string) {
    return this.http.get<Stream>(`${this.yamcs.apiUrl}/archive/${this.instance}/streams/${name}`).pipe(
      catchError(this.yamcs.handleError<Stream>())
    );
  }

  getTables() {
    return this.http.get<TablesWrapper>(`${this.yamcs.apiUrl}/archive/${this.instance}/tables`).pipe(
      map(msg => msg.table || []),
      catchError(this.yamcs.handleError<Table[]>([]))
    );
  }

  getTable(name: string) {
    return this.http.get<Table>(`${this.yamcs.apiUrl}/archive/${this.instance}/tables/${name}`).pipe(
      catchError(this.yamcs.handleError<Table>())
    );
  }

  getTableData(name: string) {
    return this.http.get<RecordsWrapper>(`${this.yamcs.apiUrl}/archive/${this.instance}/tables/${name}/data`).pipe(
      map(msg => msg.record || []),
      catchError(this.yamcs.handleError<Record[]>())
    );
  }

  getRootSpaceSystems() {
    return this.http.get<MissionDatabase>(`${this.yamcs.apiUrl}/mdb/${this.instance}`).pipe(
      map(msg => msg.spaceSystem),
      catchError(this.yamcs.handleError<SpaceSystem[]>([]))
    );
  }

  getSpaceSystems() {
    return this.http.get<SpaceSystemsWrapper>(`${this.yamcs.apiUrl}/mdb/${this.instance}/space-systems`).pipe(
      map(msg => msg.spaceSystem || []),
      catchError(this.yamcs.handleError<SpaceSystem[]>([]))
    );
  }

  getSpaceSystem(qualifiedName: string) {
    return this.http.get<SpaceSystem>(`${this.yamcs.apiUrl}/mdb/${this.instance}/space-systems${qualifiedName}`).pipe(
      catchError(this.yamcs.handleError<SpaceSystem>())
    );
  }

  getParameters(options: GetParametersOptions = {}) {
    const params = this.toParams(options);
    return this.http.get<ParametersWrapper>(`${this.yamcs.apiUrl}/mdb/${this.instance}/parameters`, { params }).pipe(
      map(msg => msg.parameter || []),
      catchError(this.yamcs.handleError<Parameter[]>([]))
    );
  }

  getParameterValueUpdates(options: ParameterSubscriptionRequest) {
    this.prepareWebSocketClient();
    return this.webSocketClient.getParameterValueUpdates(options);
  }

  getCommands(options: GetCommandsOptions = {}) {
    const params = this.toParams(options);
    return this.http.get<CommandsWrapper>(`${this.yamcs.apiUrl}/mdb/${this.instance}/commands`, { params }).pipe(
      map(msg => msg.command || []),
      catchError(this.yamcs.handleError<Command[]>([]))
    );
  }

  getCommand(qualifiedName: string) {
    return this.http.get<Command>(`${this.yamcs.apiUrl}/mdb/${this.instance}/commands${qualifiedName}`).pipe(
      catchError(this.yamcs.handleError<Command>())
    );
  }

  getContainers(options: GetContainersOptions = {}) {
    const params = this.toParams(options);
    return this.http.get<ContainersWrapper>(`${this.yamcs.apiUrl}/mdb/${this.instance}/containers`, { params }).pipe(
      map(msg => msg.container || []),
      catchError(this.yamcs.handleError<Container[]>([]))
    );
  }

  getContainer(qualifiedName: string) {
    return this.http.get<Container>(`${this.yamcs.apiUrl}/mdb/${this.instance}/containers${qualifiedName}`).pipe(
      catchError(this.yamcs.handleError<Container>())
    );
  }

  getAlgorithms(options: GetAlgorithmsOptions = {}) {
    const params = this.toParams(options);
    return this.http.get<AlgorithmsWrapper>(`${this.yamcs.apiUrl}/mdb/${this.instance}/algorithms`, { params }).pipe(
      map(msg => msg.algorithm || []),
      catchError(this.yamcs.handleError<Algorithm[]>([]))
    );
  }

  getAlgorithm(qualifiedName: string) {
    return this.http.get<Algorithm>(`${this.yamcs.apiUrl}/mdb/${this.instance}/algorithms${qualifiedName}`).pipe(
      catchError(this.yamcs.handleError<Algorithm>())
    );
  }

  getDisplayInfo() {
    return this.http.get<DisplayFolder>(`${this.yamcs.apiUrl}/displays/${this.instance}`).pipe(
      catchError(this.yamcs.handleError<DisplayFolder>())
    );
  }

  /**
   * Returns a string representation of the display definition file
   */
  getDisplay(path: string) {
    return this.http.get(`${this.yamcs.staticUrl}/${this.instance}/displays${path}`, {
      responseType: 'text',
    }).pipe(
      catchError(this.yamcs.handleError<string>())
    );
  }

  closeConnection() {
    if (this.webSocketClient) {
      this.webSocketClient.close();
    }
  }

  private prepareWebSocketClient() {
    if (!this.webSocketClient) {
      this.webSocketClient = new WebSocketClient(this.instance);
    }
  }

  private toParams(options: {[key: string]: any}) {
    let params = new HttpParams();
    for (const prop in options) {
      if (options.hasOwnProperty(prop)) {
        params = params.append(prop, String(options[prop]));
      }
    }
    return params;
  }
}
