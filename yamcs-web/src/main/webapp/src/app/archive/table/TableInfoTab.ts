import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute } from '@angular/router';
import { Table } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';
import { ShowEnumDialog } from './ShowEnumDialog';




@Component({
  templateUrl: './TableInfoTab.html',
  styleUrls: ['./TableInfoTab.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TableInfoTab {

  table$: Promise<Table>;

  constructor(route: ActivatedRoute, yamcs: YamcsService, private dialog: MatDialog) {
    const parent = route.snapshot.parent!;
    const name = parent.paramMap.get('name')!;
    this.table$ = yamcs.yamcsClient.getTable(yamcs.getInstance().name, name);
  }

  showEnum(column: string) {
    this.dialog.open(ShowEnumDialog, {
      width: '400px',
      data: { column },
    });
  }
}
