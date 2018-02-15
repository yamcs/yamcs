import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { of } from 'rxjs/observable/of';
import { catchError, map } from 'rxjs/operators';

import {
  InstancesWrapper,
  ServicesWrapper,
} from './types/internal';

import {
  Instance,
  UserInfo,
} from './types/main';
import {
  GeneralInfo,
  Service,
} from './types/system';
import { InstanceClient } from './InstanceClient';

export default class YamcsClient {

  readonly baseUrl = 'http://localhost:8090';
  readonly apiUrl = `${this.baseUrl}/api`;
  readonly staticUrl = `${this.baseUrl}/_static`;

  constructor(private http: HttpClient) {
  }

  selectInstance(instance: string) {
    return new InstanceClient(instance, this, this.http);
  }

  getGeneralInfo() {
    return this.http.get<GeneralInfo>(this.apiUrl).pipe(
      catchError(this.handleError<GeneralInfo>())
    );
  }

  /**
   * Returns info on the authenticated user
   */
  getUserInfo() {
    return this.http.get<UserInfo>(`${this.apiUrl}/user`).pipe(
      catchError(this.handleError<UserInfo>())
    );
  }

  getInstances() {
    return this.http.get<InstancesWrapper>(`${this.apiUrl}/instances`).pipe(
      map(msg => msg.instance || []),
      catchError(this.handleError<Instance[]>([]))
    );
  }

  getInstance(name: string) {
    return this.http.get<Instance>(`${this.apiUrl}/instances/${name}`).pipe(
      catchError(this.handleError<Instance>())
    );
  }

  getServices() {
    return this.http.get<ServicesWrapper>(`${this.apiUrl}/services/_global`).pipe(
      map(msg => msg.service || []),
      catchError(this.handleError<Service[]>([]))
    );
  }

  startService(name: string) {
    return this.http.patch(`${this.apiUrl}/services/_global/service/${name}`, {
      state: 'running'
    });
  }

  stopService(name: string) {
    return this.http.patch(`${this.apiUrl}/services/_global/${name}`, {
      state: 'stopped'
    });
  }

  getStaticText(path: string) {
    return this.http.get(`${this.staticUrl}/${path}`, {
      responseType: 'text',
    }).pipe(
      catchError(this.handleError<string>())
    );
  }

  getStaticXML(path: string) {
    return this.http.get(`${this.staticUrl}/${path}`, {
      responseType: 'text',
    }).pipe(
      map(text => {
        const xmlParser = new DOMParser();
        const doc = xmlParser.parseFromString(text, 'text/xml');
        return doc as XMLDocument;
      }),
      catchError(this.handleError<XMLDocument>())
    );
  }

  handleError<T> (errorReplacement?: T) {
    return (error: any): Observable<T> => {
      console.error(error);

      // Return empty result
      return of(errorReplacement as T);
    };
  }
}
