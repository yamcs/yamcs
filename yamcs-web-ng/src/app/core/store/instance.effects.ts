import { Injectable } from '@angular/core';
import { Effect, Actions } from '@ngrx/effects';

import * as instanceActions from './instance.actions';
import {
  LoadInstancesSuccessAction, LoadInstancesFailAction
} from './instance.actions';

import YamcsClient from '../../../yamcs-client/YamcsClient';
import { catchError, switchMap, map } from 'rxjs/operators';
import { of } from 'rxjs/observable/of';

@Injectable()
export class InstanceEffects {

  @Effect()
  loadInstances$ = this.actions$.ofType(instanceActions.LOAD).pipe(
    switchMap(() => {
      return this.yamcs.getInstances().pipe(
        map(instances => new LoadInstancesSuccessAction(instances)),
        catchError(err => of(new LoadInstancesFailAction(err))),
      );
    })
  );

  constructor(private actions$: Actions, private yamcs: YamcsClient) {
  }
}
