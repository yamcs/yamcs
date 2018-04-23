import { Component, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Instance, Command } from '@yamcs/client';
import { YamcsService } from '../../core/services/YamcsService';
import { Title } from '@angular/platform-browser';

@Component({
  templateUrl: './CommandPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandPage {

  instance: Instance;
  command$: Promise<Command>;

  constructor(route: ActivatedRoute, yamcs: YamcsService, title: Title) {
    this.instance = yamcs.getInstance();

    const qualifiedName = route.snapshot.paramMap.get('qualifiedName')!;
    this.command$ = yamcs.getInstanceClient()!.getCommand(qualifiedName);
    this.command$.then(command => {
      title.setTitle(command.name + ' - Yamcs');
    });
  }
}
