import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Store } from '@ngrx/store';
import { Observable } from 'rxjs/Observable';

import { Table, Record, YamcsClient } from '../../../yamcs-client';
import { selectCurrentInstance } from '../../core/store/instance.selectors';
import { State } from '../../app.reducers';

import { switchMap } from 'rxjs/operators';
import { ActivatedRoute } from '@angular/router';

import * as utils from '../utils';

@Component({
  templateUrl: './table.component.html',
  styleUrls: ['./streamsql.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TablePageComponent {

  table$: Observable<Table>;
  records$: Observable<Record[]>;

  constructor(route: ActivatedRoute, store: Store<State>, yamcs: YamcsClient) {
    const name = route.snapshot.paramMap.get('name');
    if (name != null) {
      this.table$ = store.select(selectCurrentInstance).pipe(
        switchMap(instance => yamcs.getTable(instance.name, name)),
      );

      this.records$ = store.select(selectCurrentInstance).pipe(
        switchMap(instance => yamcs.getTableData(instance.name, name)),
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

  formatSQL(sql: string) {
    return utils.formatSQL(sql);
  }
}
