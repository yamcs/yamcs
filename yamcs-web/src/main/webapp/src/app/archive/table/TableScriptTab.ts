import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Table } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';
import * as utils from '../utils';




@Component({
  templateUrl: './TableScriptTab.html',
  styleUrls: [
    './TableScriptTab.css',
    '../streamsql.css',
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TableScriptTab {

  table$: Promise<Table>;

  constructor(route: ActivatedRoute, yamcs: YamcsService) {
    const parent = route.snapshot.parent!;
    const name = parent.paramMap.get('name')!;
    this.table$ = yamcs.yamcsClient.getTable(yamcs.getInstance(), name);
  }

  formatSQL(sql: string) {
    return utils.formatSQL(sql);
  }
}
