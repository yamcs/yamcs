import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { of } from 'rxjs/observable/of';
import { catchError, map, tap } from 'rxjs/operators';

import { InstancesWrapper } from './types/internal';
import { Instance } from './types/main';

export default class YamcsClient {

  public baseUrl = 'http://localhost:8090';

  constructor(private http: HttpClient) {
  }

  getInstances() {
    return this.http.get<InstancesWrapper>(`${this.baseUrl}/api/instances`).pipe(
      map(msg => msg.instance),
      tap(instances => console.log('Fetched instances', instances)),
      catchError(this.handleError<Instance[]>([]))
    );
  }

  getInstance(name: string) {
    return this.http.get<Instance>(`${this.baseUrl}/api/instances/${name}`).pipe(
      tap(instance => console.log('Fetched instance', instance)),
      catchError(this.handleError<Instance>())
    );
  }

  getEvents(instance: string) {
    return this.http.get(`${this.baseUrl}/api/archive/${instance}/events`);
  }

  private handleError<T> (errorReplacement?: T) {
    return (error: any): Observable<T> => {
      console.error(error);

      // Return empty result
      return of(errorReplacement as T);
    };
  }
}
