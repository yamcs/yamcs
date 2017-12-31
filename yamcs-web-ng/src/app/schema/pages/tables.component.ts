import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Store } from '@ngrx/store';
import { Observable } from 'rxjs/Observable';

import { YamcsClient, Table } from '../../../yamcs-client';
import { selectCurrentInstance } from '../../core/store/instance.selectors';
import { State } from '../../app.reducers';

import { switchMap } from 'rxjs/operators';
import { HttpClient } from '@angular/common/http';

@Component({
  templateUrl: './tables.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TablesPageComponent {

  tables$: Observable<Table[]>;

  constructor(store: Store<State>, http: HttpClient) {
    this.tables$ = store.select(selectCurrentInstance).pipe(
      switchMap(instance => {
        const yamcs = new YamcsClient(http);
        return yamcs.getTables(instance.name);
      }),
    );
  }
}
