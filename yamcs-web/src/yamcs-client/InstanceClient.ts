import { HttpClient } from '@angular/common/http';
import { catchError, filter, map } from 'rxjs/operators';
import { WebSocketClient } from './WebSocketClient';

import YamcsClient from './YamcsClient';

import {
  CommandsWrapper,
  LinksWrapper,
  ParametersWrapper,
  RecordsWrapper,
  ServicesWrapper,
  StreamsWrapper,
  TablesWrapper,
} from './types/internal';

import {
  Command,
  DisplayInfo,
  Link,
  Parameter,
  Record,
  Service,
  Stream,
  Table,
  ParameterSubscriptionRequest,
} from './types/main';

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

  getLinks() {
    return this.http.get<LinksWrapper>(`${this.yamcs.apiUrl}/links/${this.instance}`).pipe(
      map(msg => msg.link),
      catchError(this.yamcs.handleError<Link[]>([]))
    );
  }

  getLinkUpdates() {
    this.prepareWebSocketClient();
    return this.webSocketClient.getLinkUpdates();
  }

  getClientUpdates() {
    this.prepareWebSocketClient();
    return this.webSocketClient.getClientUpdates().pipe(
      filter(msg => msg.instance === this.instance)
    );
  }

  getServices() {
    return this.http.get<ServicesWrapper>(`${this.yamcs.apiUrl}/services/${this.instance}`).pipe(
      map(msg => msg.service),
      catchError(this.yamcs.handleError<Service[]>([]))
    );
  }

  getStreams() {
    return this.http.get<StreamsWrapper>(`${this.yamcs.apiUrl}/archive/${this.instance}/streams`).pipe(
      map(msg => msg.stream),
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
      map(msg => msg.table),
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
      map(msg => msg.record),
      catchError(this.yamcs.handleError<Record[]>())
    );
  }

  getEvents() {
    return this.http.get(`${this.yamcs.apiUrl}/archive/${this.instance}/events`);
  }

  getParameters() {
    return this.http.get<ParametersWrapper>(`${this.yamcs.apiUrl}/mdb/${this.instance}/parameters`).pipe(
      map(msg => msg.parameter),
      catchError(this.yamcs.handleError<Parameter[]>([]))
    );
  }

  getParameterValueUpdates(options: ParameterSubscriptionRequest) {
    this.prepareWebSocketClient();
    return this.webSocketClient.getParameterValueUpdates(options);
  }

  getCommands() {
    return this.http.get<CommandsWrapper>(`${this.yamcs.apiUrl}/mdb/${this.instance}/commands`).pipe(
      map(msg => msg.command),
      catchError(this.yamcs.handleError<Command[]>([]))
    );
  }

  getDisplayInfo() {
    return this.http.get<DisplayInfo>(`${this.yamcs.apiUrl}/displays/${this.instance}`).pipe(
      catchError(this.yamcs.handleError<DisplayInfo>())
    );
  }

  /**
   * Returns a string representation of the display definition file
   */
  getDisplay(path: string) {
    return this.http.get(`${this.yamcs.staticUrl}/${this.instance}/displays/${path}`, {
      responseType: 'text',
    }).pipe(
      catchError(this.yamcs.handleError<string>())
    );
  }

  private prepareWebSocketClient() {
    if (!this.webSocketClient) {
      this.webSocketClient = new WebSocketClient(this.instance);
    }
  }
}
