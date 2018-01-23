import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Observable } from 'rxjs/Observable';

import { Command } from '../../../yamcs-client';

import { YamcsService } from '../../core/services/yamcs.service';

@Component({
  templateUrl: './commands.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandsPageComponent {

  commands$: Observable<Command[]>;

  constructor(yamcs: YamcsService) {
    this.commands$ = yamcs.getSelectedInstance().getCommands();
  }
}
