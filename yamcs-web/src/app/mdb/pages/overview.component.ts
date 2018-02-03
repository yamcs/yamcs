import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Store } from '@ngrx/store';
import { Observable } from 'rxjs/Observable';

import { Instance } from '../../../yamcs-client';
import { selectInstances } from '../../core/store/instance.selectors';
import { State } from '../../app.reducers';

@Component({
  templateUrl: './overview.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OverviewPageComponent {

  instances$: Observable<Instance[]>;

  constructor(store: Store<State>) {
    this.instances$ = store.select(selectInstances);
  }
}
