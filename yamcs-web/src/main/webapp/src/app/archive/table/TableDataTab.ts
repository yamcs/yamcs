import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { Record, Table } from '../../client';
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
    this.table$ = yamcs.yamcsClient.getTable(yamcs.getInstance().name, name);
    this.records$ = yamcs.yamcsClient.getTableData(yamcs.getInstance().name, name);
  }

  selectRecord(record: Record) {
    this.selectedRecord$.next(record);
  }
}
