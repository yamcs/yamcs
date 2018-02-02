import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Instance } from '../../../yamcs-client';
import { Observable } from 'rxjs/Observable';
import { Store } from '@ngrx/store';
import { selectInstances } from '../store/instance.selectors';
import { State } from '../../app.reducers';

@Component({
  templateUrl: './home.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomePageComponent {

  instances$: Observable<Instance[]>;

  constructor(store: Store<State>) {
    this.instances$ = store.select(selectInstances);
  }
}
