import { Component, ChangeDetectionStrategy } from '@angular/core';

import { Table } from '@yamcs/client';

import { ActivatedRoute } from '@angular/router';

import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './TableColumnsTab.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TableColumnsTab {

  table$: Promise<Table>;

  constructor(route: ActivatedRoute, yamcs: YamcsService) {
    const parent = route.snapshot.parent!;
    const name = parent.paramMap.get('name')!;
    this.table$ = yamcs.getInstanceClient().getTable(name);
  }
}
