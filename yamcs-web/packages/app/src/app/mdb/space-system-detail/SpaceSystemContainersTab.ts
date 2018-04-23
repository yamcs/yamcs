import { Component, ChangeDetectionStrategy } from '@angular/core';

import { Instance, Container } from '@yamcs/client';

import { ActivatedRoute } from '@angular/router';

import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './SpaceSystemContainersTab.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SpaceSystemContainersTab {

  qualifiedName: string;

  instance: Instance;
  containers$: Promise<Container[]>;

  constructor(route: ActivatedRoute, yamcs: YamcsService) {
    const parent = route.snapshot.parent!;
    this.qualifiedName = parent.paramMap.get('qualifiedName')!;
    this.containers$ = yamcs.getInstanceClient()!.getContainers({
      namespace: this.qualifiedName
    });
    this.instance = yamcs.getInstance();
  }
}
