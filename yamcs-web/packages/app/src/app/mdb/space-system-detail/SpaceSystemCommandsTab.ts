import { Component, ChangeDetectionStrategy } from '@angular/core';

import { Instance, Command } from '@yamcs/client';

import { ActivatedRoute } from '@angular/router';

import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './SpaceSystemCommandsTab.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SpaceSystemCommandsTab {

  qualifiedName: string;

  instance: Instance;
  commands$: Promise<Command[]>;

  constructor(route: ActivatedRoute, yamcs: YamcsService) {
    const parent = route.snapshot.parent!;
    this.qualifiedName = parent.paramMap.get('qualifiedName')!;
    this.commands$ = yamcs.getInstanceClient()!.getCommands({
      namespace: this.qualifiedName
    });
    this.instance = yamcs.getInstance();
  }
}
