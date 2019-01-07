import { Component, ChangeDetectionStrategy } from '@angular/core';

import { Table } from '@yamcs/client';

import { ActivatedRoute } from '@angular/router';
import * as utils from '../utils';

import { YamcsService } from '../../core/services/YamcsService';

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
    this.table$ = yamcs.getInstanceClient()!.getTable(name);
  }

  formatSQL(sql: string) {
    return utils.formatSQL(sql);
  }
}
