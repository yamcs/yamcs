import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Observable } from 'rxjs/Observable';

import { Command } from '../../../yamcs-client';

import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './CommandsPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandsPage {

  commands$: Observable<Command[]>;

  constructor(yamcs: YamcsService) {
    this.commands$ = yamcs.getSelectedInstance().getCommands();
  }
}
