import { Component, ChangeDetectionStrategy, OnInit } from '@angular/core';
import { Observable } from 'rxjs/Observable';

import { Table, Record, Instance } from '../../../yamcs-client';

import { ActivatedRoute } from '@angular/router';

import * as utils from '../utils';
import { YamcsService } from '../../core/services/yamcs.service';
import { State } from '../../app.reducers';
import { Store } from '@ngrx/store';
import { selectCurrentInstance } from '../../core/store/instance.selectors';
import { BehaviorSubject } from 'rxjs/BehaviorSubject';

@Component({
  templateUrl: './table.component.html',
  styleUrls: ['./table.component.css', './streamsql.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TablePageComponent implements OnInit {

  instance$: Observable<Instance>;
  table$: Observable<Table>;
  records$: Observable<Record[]>;

  selectedRecord$ = new BehaviorSubject<Record | null>(null);

  constructor(route: ActivatedRoute, yamcs: YamcsService, private store: Store<State>) {
    const name = route.snapshot.paramMap.get('name');
    if (name != null) {
      this.table$ = yamcs.getSelectedInstance().getTable(name);
      this.records$ = yamcs.getSelectedInstance().getTableData(name);
    }
  }

  ngOnInit() {
    this.instance$ = this.store.select(selectCurrentInstance);
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

  selectRecord(record: Record) {
    this.selectedRecord$.next(record);
  }
}
