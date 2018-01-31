import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Observable } from 'rxjs/Observable';

import { Table } from '../../../yamcs-client';

import { YamcsService } from '../../core/services/yamcs.service';

@Component({
  templateUrl: './tables.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TablesPageComponent {

  tables$: Observable<Table[]>;

  constructor(yamcs: YamcsService) {
    this.tables$ = yamcs.getSelectedInstance().getTables();
  }
}
