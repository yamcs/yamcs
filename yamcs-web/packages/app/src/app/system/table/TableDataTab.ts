import { Component, ChangeDetectionStrategy } from '@angular/core';

import { Table, Record, Value } from '@yamcs/client';
import { BehaviorSubject } from 'rxjs';

import { ActivatedRoute } from '@angular/router';

import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './TableDataTab.html',
  styleUrls: ['./TableDataTab.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TableDataTab {

  table$: Promise<Table>;
  records$: Promise<Record[]>;

  selectedRecord$ = new BehaviorSubject<Record | null>(null);

  constructor(route: ActivatedRoute, yamcs: YamcsService) {
    const parent = route.snapshot.parent!;
    const name = parent.paramMap.get('name')!;
    this.table$ = yamcs.getInstanceClient()!.getTable(name);
    this.records$ = yamcs.getInstanceClient()!.getTableData(name);
  }

  getColumnValue(record: Record, columnName: string): Value | null {
    for (const column of record.column) {
      if (column.name === columnName) {
        return column.value;
      }
    }
    return null;
  }

  selectRecord(record: Record) {
    this.selectedRecord$.next(record);
  }
}
