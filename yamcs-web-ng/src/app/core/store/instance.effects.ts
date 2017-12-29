import { Injectable } from '@angular/core';
import { Effect, Actions } from '@ngrx/effects';

import * as instanceActions from './instance.actions';
import {
  LoadInstancesSuccessAction, LoadInstancesFailAction
} from './instance.actions';

import { HttpClient } from '@angular/common/http';
import YamcsClient from '../../../yamcs-client/YamcsClient';
import { catchError, switchMap, map } from 'rxjs/operators';
import { of } from 'rxjs/observable/of';

@Injectable()
export class InstanceEffects {

  private yamcs: YamcsClient;

  @Effect()
  loadInstances$ = this.actions$.ofType(instanceActions.LOAD).pipe(
    switchMap(() => {
      return this.yamcs.getInstances().pipe(
        map(instances => new LoadInstancesSuccessAction(instances)),
        catchError(err => of(new LoadInstancesFailAction(err))),
      );
    })
  );

  constructor(private actions$: Actions, http: HttpClient) {
    this.yamcs = new YamcsClient(http);
  }
}
