import { Component, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Store } from '@ngrx/store';
import { State } from '../../app.reducers';
import { SelectInstanceAction } from '../store/instance.actions';

@Component({
  template: '<router-outlet></router-outlet>',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InstancePageComponent {

  constructor(route: ActivatedRoute, store: Store<State>) {
    const instanceName = route.snapshot.paramMap.get('instance');
    if (instanceName !== null) {
      store.dispatch(new SelectInstanceAction(instanceName));
    }
  }
}
