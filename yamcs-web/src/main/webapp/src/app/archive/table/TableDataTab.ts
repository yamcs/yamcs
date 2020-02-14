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
    this.table$ = yamcs.getInstanceClient()!.getTable(name);
    this.records$ = yamcs.getInstanceClient()!.getTableData(name);
  }

  selectRecord(record: Record) {
    this.selectedRecord$.next(record);
  }
}
