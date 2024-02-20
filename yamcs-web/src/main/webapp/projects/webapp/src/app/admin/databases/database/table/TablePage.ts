import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { Table, YamcsService } from '@yamcs/webapp-sdk';

@Component({
  templateUrl: './TablePage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TablePage {

  table$: Promise<Table>;

  constructor(route: ActivatedRoute, readonly yamcs: YamcsService, title: Title) {
    const database = route.snapshot.parent!.paramMap.get('database')!;
    const name = route.snapshot.paramMap.get('table')!;
    title.setTitle(name);
    this.table$ = yamcs.yamcsClient.getTable(database, name);
  }
}
