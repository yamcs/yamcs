import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Observable } from 'rxjs/Observable';

import { Command, Instance } from '../../../yamcs-client';

import { YamcsService } from '../../core/services/YamcsService';
import { State } from '../../app.reducers';
import { Store } from '@ngrx/store';
import { selectCurrentInstance } from '../../core/store/instance.selectors';

@Component({
  templateUrl: './CommandsPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandsPage {

  instance$: Observable<Instance>;
  commands$: Observable<Command[]>;

  constructor(yamcs: YamcsService, store: Store<State>) {
    this.instance$ = store.select(selectCurrentInstance);
    this.commands$ = yamcs.getSelectedInstance().getCommands();
  }
}
