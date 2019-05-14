import { Component, ChangeDetectionStrategy } from '@angular/core';

import { Table } from '@yamcs/client';

import { ActivatedRoute } from '@angular/router';

import { YamcsService } from '../../core/services/YamcsService';
import { MatDialog } from '@angular/material';
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
    this.table$ = yamcs.getInstanceClient()!.getTable(name);
  }

  showEnum(column: string) {
    this.dialog.open(ShowEnumDialog, {
      width: '400px',
      data: { column },
    });
  }
}
