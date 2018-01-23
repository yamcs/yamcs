import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { of } from 'rxjs/observable/of';
import { catchError, map } from 'rxjs/operators';

import {
  InstancesWrapper,
} from './types/internal';

import {
  Instance,
} from './types/main';
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

  getInstances() {
    return this.http.get<InstancesWrapper>(`${this.apiUrl}/instances`).pipe(
      map(msg => msg.instance),
      catchError(this.handleError<Instance[]>([]))
    );
  }

  getInstance(name: string) {
    return this.http.get<Instance>(`${this.apiUrl}/instances/${name}`).pipe(
      catchError(this.handleError<Instance>())
    );
  }

  getStaticText(path: string) {
    return this.http.get(`${this.staticUrl}/${path}`, {
      responseType: 'text',
    }).pipe(
      catchError(this.handleError<string>())
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
