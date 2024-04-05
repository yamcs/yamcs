import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Record, Table, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { RecordComponent } from '../record/record.component';
import { ColumnValuePipe } from '../shared/column-value.pipe';

@Component({
  standalone: true,
  templateUrl: './table-data-tab.component.html',
  styleUrl: './table-data-tab.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ColumnValuePipe,
    RecordComponent,
    WebappSdkModule,
  ],
})
export class TableDataTabComponent {

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
