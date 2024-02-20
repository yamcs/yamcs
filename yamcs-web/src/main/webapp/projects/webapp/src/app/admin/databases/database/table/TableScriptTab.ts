import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Table, YamcsService } from '@yamcs/webapp-sdk';
import * as utils from '../../utils';


@Component({
  templateUrl: './TableScriptTab.html',
  styleUrls: [
    './TableScriptTab.css',
    '../../streamsql.css',
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TableScriptTab {

  table$: Promise<Table>;

  constructor(route: ActivatedRoute, yamcs: YamcsService) {
    const parent = route.snapshot.parent!;
    const database = parent.parent!.paramMap.get('database')!;
    const name = parent.paramMap.get('table')!;
    this.table$ = yamcs.yamcsClient.getTable(database, name);
  }

  formatSQL(sql: string) {
    return utils.formatSQL(sql);
  }
}
