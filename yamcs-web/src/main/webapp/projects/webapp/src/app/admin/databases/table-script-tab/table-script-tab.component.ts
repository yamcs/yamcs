import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Table, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import * as utils from '../utils';

@Component({
  standalone: true,
  selector: 'app-table-script-tab',
  templateUrl: './table-script-tab.component.html',
  styleUrls: [
    './table-script-tab.component.css',
    '../streamsql.css',
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class TableScriptTabComponent {

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
