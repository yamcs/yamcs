import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { Table } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './TablePage.html',
  styleUrls: ['./TablePage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TablePage {

  table$: Promise<Table>;

  constructor(route: ActivatedRoute, readonly yamcs: YamcsService, title: Title) {
    const name = route.snapshot.paramMap.get('name')!;
    title.setTitle(name);
    this.table$ = yamcs.yamcsClient.getTable(yamcs.instance!, name);
  }
}
