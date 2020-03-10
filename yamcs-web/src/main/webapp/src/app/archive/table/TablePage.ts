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

  instance: string;
  table$: Promise<Table>;

  constructor(route: ActivatedRoute, yamcs: YamcsService, title: Title) {
    const name = route.snapshot.paramMap.get('name')!;
    title.setTitle(name);
    this.table$ = yamcs.yamcsClient.getTable(this.instance, name);
    this.instance = yamcs.getInstance();
  }
}
