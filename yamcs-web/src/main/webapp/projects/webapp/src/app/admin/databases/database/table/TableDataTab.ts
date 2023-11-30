import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Record, Table, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';

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
    const database = parent.parent!.paramMap.get('database')!;
    const name = parent.paramMap.get('table')!;
    this.table$ = yamcs.yamcsClient.getTable(database, name);
    this.records$ = yamcs.yamcsClient.getTableData(database, name);
  }

  selectRecord(record: Record) {
    this.selectedRecord$.next(record);
  }
}
