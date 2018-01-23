import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Observable } from 'rxjs/Observable';

import { Table, Record } from '../../../yamcs-client';

import { ActivatedRoute } from '@angular/router';

import * as utils from '../utils';
import { YamcsService } from '../../core/services/yamcs.service';

@Component({
  templateUrl: './table.component.html',
  styleUrls: ['./streamsql.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TablePageComponent {

  table$: Observable<Table>;
  records$: Observable<Record[]>;

  constructor(route: ActivatedRoute, yamcs: YamcsService) {
    const name = route.snapshot.paramMap.get('name');
    if (name != null) {
      this.table$ = yamcs.getSelectedInstance().getTable(name);
      this.records$ = yamcs.getSelectedInstance().getTableData(name);
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
