import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { of } from 'rxjs/observable/of';
import { catchError, map } from 'rxjs/operators';

import {
  CommandsWrapper,
  InstancesWrapper,
  ParametersWrapper,
  RecordsWrapper,
  ServicesWrapper,
  StreamsWrapper,
  TablesWrapper,
} from './types/internal';

import {
  Command,
  Instance,
  Parameter,
  Record,
  Service,
  Stream,
  Table,
} from './types/main';

export default class YamcsClient {

  public baseUrl = 'http://localhost:8090';

  constructor(private http: HttpClient) {
  }

  getInstances() {
    return this.http.get<InstancesWrapper>(`${this.baseUrl}/api/instances`).pipe(
      map(msg => msg.instance),
      // tap(instances => console.log('Fetched instances', instances)),
      catchError(this.handleError<Instance[]>([]))
    );
  }

  getInstance(name: string) {
    return this.http.get<Instance>(`${this.baseUrl}/api/instances/${name}`).pipe(
      // tap(instance => console.log('Fetched instance', instance)),
      catchError(this.handleError<Instance>())
    );
  }

  getServices(instance: string) {
    return this.http.get<ServicesWrapper>(`${this.baseUrl}/api/services/${instance}`).pipe(
      map(msg => msg.service),
      catchError(this.handleError<Service[]>([]))
    );
  }

  getStreams(instance: string) {
    return this.http.get<StreamsWrapper>(`${this.baseUrl}/api/archive/${instance}/streams`).pipe(
      map(msg => msg.stream),
      catchError(this.handleError<Stream[]>([]))
    );
  }

  getStream(instance: string, name: string) {
    return this.http.get<Stream>(`${this.baseUrl}/api/archive/${instance}/streams/${name}`).pipe(
      catchError(this.handleError<Stream>())
    );
  }

  getTables(instance: string) {
    return this.http.get<TablesWrapper>(`${this.baseUrl}/api/archive/${instance}/tables`).pipe(
      map(msg => msg.table),
      catchError(this.handleError<Table[]>([]))
    );
  }

  getTable(instance: string, name: string) {
    return this.http.get<Table>(`${this.baseUrl}/api/archive/${instance}/tables/${name}`).pipe(
      catchError(this.handleError<Table>())
    );
  }

  getTableData(instance: string, name: string) {
    return this.http.get<RecordsWrapper>(`${this.baseUrl}/api/archive/${instance}/tables/${name}/data`).pipe(
      map(msg => msg.record),
      catchError(this.handleError<Record[]>())
    );
  }

  getEvents(instance: string) {
    return this.http.get(`${this.baseUrl}/api/archive/${instance}/events`);
  }

  getParameters(instance: string) {
    return this.http.get<ParametersWrapper>(`${this.baseUrl}/api/mdb/${instance}/parameters`).pipe(
      map(msg => msg.parameter),
      catchError(this.handleError<Parameter[]>([]))
    );
  }

  getCommands(instance: string) {
    return this.http.get<CommandsWrapper>(`${this.baseUrl}/api/mdb/${instance}/commands`).pipe(
      map(msg => msg.command),
      catchError(this.handleError<Command[]>([]))
    );
  }

  private handleError<T> (errorReplacement?: T) {
    return (error: any): Observable<T> => {
      console.error(error);

      // Return empty result
      return of(errorReplacement as T);
    };
  }
}
