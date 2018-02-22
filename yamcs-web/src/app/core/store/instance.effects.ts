import { Injectable } from '@angular/core';
import { Effect, Actions } from '@ngrx/effects';

import * as instanceActions from './instance.actions';
import {
  LoadInstancesSuccessAction,
  LoadInstancesFailAction,
  SelectInstanceAction,
} from './instance.actions';

import { catchError, switchMap, map, tap } from 'rxjs/operators';
import { of } from 'rxjs/observable/of';
import { HttpClient } from '@angular/common/http';

@Injectable()
export class InstanceEffects {

  @Effect()
  loadInstances$ = this.actions$.ofType(instanceActions.LOAD).pipe(
    switchMap(() => {
      return this.http.get<any>('/api/instances').pipe(
        map(msg => msg.instance),
        map(instances => new LoadInstancesSuccessAction(instances)),
        catchError(err => of(new LoadInstancesFailAction(err))),
      );
    })
  );

  @Effect({ dispatch: false })
  connectInstance$ = this.actions$.ofType(instanceActions.SELECT).pipe(
    tap((action: SelectInstanceAction) => {
      // const instance = action.payload;
      // this.yamcs.connectTo(instance);
    })
  );

  constructor(
    private actions$: Actions,
    private http: HttpClient) {
  }
}
