import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { Command, Instance } from '@yamcs/client';
import { YamcsService } from '../../core/services/YamcsService';



@Component({
  templateUrl: './CommandsPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandsPage {

  instance: Instance;
  commands$: Promise<Command[]>;

  constructor(yamcs: YamcsService, title: Title) {
    title.setTitle('Commands - Yamcs');
    this.instance = yamcs.getInstance();
    this.commands$ = yamcs.getInstanceClient()!.getCommands();
  }
}
