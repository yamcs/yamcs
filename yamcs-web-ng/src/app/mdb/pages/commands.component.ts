import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Store } from '@ngrx/store';
import { Observable } from 'rxjs/Observable';

import { Instance, Command, YamcsClient } from '../../../yamcs-client';
import { selectCurrentInstance } from '../../core/store/instance.selectors';
import { State } from '../../app.reducers';

import { switchMap } from 'rxjs/operators';

@Component({
  templateUrl: './commands.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandsPageComponent {

  instance$: Observable<Instance>;
  commands$: Observable<Command[]>;

  constructor(store: Store<State>, yamcs: YamcsClient) {
    this.instance$ = store.select(selectCurrentInstance);

    this.commands$ = store.select(selectCurrentInstance).pipe(
      switchMap(instance => yamcs.getCommands(instance.name)),
    );
  }
}
