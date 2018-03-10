import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Observable } from 'rxjs/Observable';

import { Algorithm, Instance } from '@yamcs/client';

import { YamcsService } from '../../core/services/YamcsService';
import { Store } from '@ngrx/store';
import { State } from '../../app.reducers';
import { selectCurrentInstance } from '../../core/store/instance.selectors';

@Component({
  templateUrl: './AlgorithmsPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlgorithmsPage {

  instance$: Observable<Instance>;
  algorithms$: Promise<Algorithm[]>;

  constructor(yamcs: YamcsService, store: Store<State>) {
    this.instance$ = store.select(selectCurrentInstance);
    this.algorithms$ = yamcs.getSelectedInstance().getAlgorithms();
  }
}
