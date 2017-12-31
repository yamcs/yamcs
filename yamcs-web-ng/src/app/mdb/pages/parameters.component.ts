import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Store } from '@ngrx/store';
import { Observable } from 'rxjs/Observable';

import { Instance, Parameter, YamcsClient } from '../../../yamcs-client';
import { selectCurrentInstance } from '../../core/store/instance.selectors';
import { State } from '../../app.reducers';

import { switchMap } from 'rxjs/operators';
import { HttpClient } from '@angular/common/http';

@Component({
  templateUrl: './parameters.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParametersPageComponent {

  instance$: Observable<Instance>;
  parameters$: Observable<Parameter[]>;

  constructor(store: Store<State>, http: HttpClient) {
    this.instance$ = store.select(selectCurrentInstance);

    this.parameters$ = store.select(selectCurrentInstance).pipe(
      switchMap(instance => {
        const yamcs = new YamcsClient(http);
        return yamcs.getParameters(instance.name);
      }),
    );
  }
}
