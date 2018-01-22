import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Store } from '@ngrx/store';
import { Observable } from 'rxjs/Observable';

import { Instance, Parameter, YamcsClient } from '../../../yamcs-client';
import { selectCurrentInstance } from '../../core/store/instance.selectors';
import { State } from '../../app.reducers';

import { switchMap } from 'rxjs/operators';

@Component({
  templateUrl: './parameters.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParametersPageComponent {

  instance$: Observable<Instance>;
  parameters$: Observable<Parameter[]>;

  constructor(store: Store<State>, yamcs: YamcsClient) {
    this.instance$ = store.select(selectCurrentInstance);

    this.parameters$ = store.select(selectCurrentInstance).pipe(
      switchMap(instance => yamcs.getParameters(instance.name)),
    );
  }
}
