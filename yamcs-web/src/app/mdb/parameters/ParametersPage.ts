import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Observable } from 'rxjs/Observable';

import { Parameter, Instance } from '../../../yamcs-client';

import { YamcsService } from '../../core/services/YamcsService';
import { Store } from '@ngrx/store';
import { State } from '../../app.reducers';
import { selectCurrentInstance } from '../../core/store/instance.selectors';

@Component({
  templateUrl: './ParametersPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParametersPage {

  instance$: Observable<Instance>;
  parameters$: Observable<Parameter[]>;

  constructor(yamcs: YamcsService, store: Store<State>) {
    this.instance$ = store.select(selectCurrentInstance);
    this.parameters$ = yamcs.getSelectedInstance().getParameters();
  }
}
