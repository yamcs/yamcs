import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute } from '@angular/router';
import { Column, Table, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { ShowEnumDialogComponent } from '../show-enum-dialog/show-enum-dialog.component';

@Component({
  standalone: true,
  templateUrl: './table-info-tab.component.html',
  styleUrl: './table-info-tab.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class TableInfoTabComponent {

  table$: Promise<Table>;

  constructor(route: ActivatedRoute, yamcs: YamcsService, private dialog: MatDialog) {
    const parent = route.snapshot.parent!;
    const database = parent.parent!.paramMap.get('database')!;
    const name = parent.paramMap.get('table')!;
    this.table$ = yamcs.yamcsClient.getTable(database, name);
  }

  showEnum(column: Column) {
    this.dialog.open(ShowEnumDialogComponent, {
      width: '400px',
      data: { column },
    });
  }
}
