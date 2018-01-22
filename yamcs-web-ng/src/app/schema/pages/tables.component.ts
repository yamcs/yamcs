import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Store } from '@ngrx/store';
import { Observable } from 'rxjs/Observable';

import { YamcsClient, Table } from '../../../yamcs-client';
import { selectCurrentInstance } from '../../core/store/instance.selectors';
import { State } from '../../app.reducers';

import { switchMap } from 'rxjs/operators';

@Component({
  templateUrl: './tables.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TablesPageComponent {

  tables$: Observable<Table[]>;

  constructor(store: Store<State>, yamcs: YamcsClient) {
    this.tables$ = store.select(selectCurrentInstance).pipe(
      switchMap(instance => yamcs.getTables(instance.name)),
    );
  }
}
