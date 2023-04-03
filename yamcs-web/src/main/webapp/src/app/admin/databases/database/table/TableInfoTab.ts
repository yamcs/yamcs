import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatLegacyDialog } from '@angular/material/legacy-dialog';
import { ActivatedRoute } from '@angular/router';
import { Table } from '../../../../client';
import { YamcsService } from '../../../../core/services/YamcsService';
import { ShowEnumDialog } from './ShowEnumDialog';

@Component({
  templateUrl: './TableInfoTab.html',
  styleUrls: ['./TableInfoTab.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TableInfoTab {

  table$: Promise<Table>;

  constructor(route: ActivatedRoute, yamcs: YamcsService, private dialog: MatLegacyDialog) {
    const parent = route.snapshot.parent!;
    const database = parent.parent!.paramMap.get('database')!;
    const name = parent.paramMap.get('table')!;
    this.table$ = yamcs.yamcsClient.getTable(database, name);
  }

  showEnum(column: string) {
    this.dialog.open(ShowEnumDialog, {
      width: '400px',
      data: { column },
    });
  }
}
