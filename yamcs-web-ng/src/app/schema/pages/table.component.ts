import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Store } from '@ngrx/store';
import { Observable } from 'rxjs/Observable';

import { Table, Record, YamcsClient } from '../../../yamcs-client';
import { selectCurrentInstance } from '../../core/store/instance.selectors';
import { State } from '../../app.reducers';

import { switchMap } from 'rxjs/operators';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';

@Component({
  templateUrl: './table.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TablePageComponent {

  table$: Observable<Table>;
  records$: Observable<Record[]>;

  constructor(route: ActivatedRoute, store: Store<State>, http: HttpClient) {
    const name = route.snapshot.paramMap.get('name');
    if (name != null) {
      this.table$ = store.select(selectCurrentInstance).pipe(
        switchMap(instance => {
          const yamcs = new YamcsClient(http);
          return yamcs.getTable(instance.name, name);
        }),
      );

      this.records$ = store.select(selectCurrentInstance).pipe(
        switchMap(instance => {
          const yamcs = new YamcsClient(http);
          return yamcs.getTableData(instance.name, name);
        }),
      );
    }
  }

  getColumnValue(record: Record, columnName: string) {
    for (const column of record.column) {
      if (column.name === columnName) {
        return column.value;
      }
    }
    return null;
  }
}
