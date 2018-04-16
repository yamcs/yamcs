import { Component, ChangeDetectionStrategy } from '@angular/core';

import { Table, Instance } from '@yamcs/client';

import { ActivatedRoute } from '@angular/router';

import { YamcsService } from '../../core/services/YamcsService';
import { Title } from '@angular/platform-browser';

@Component({
  templateUrl: './TablePage.html',
  styleUrls: ['./TablePage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TablePage {

  instance: Instance;
  table$: Promise<Table>;

  constructor(route: ActivatedRoute, yamcs: YamcsService, title: Title) {
    const name = route.snapshot.paramMap.get('name')!;
    title.setTitle(name + ' - Yamcs');
    this.table$ = yamcs.getInstanceClient()!.getTable(name);
    this.instance = yamcs.getInstance();
  }
}
