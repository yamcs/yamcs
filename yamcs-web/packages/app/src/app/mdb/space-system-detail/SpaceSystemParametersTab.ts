import { Component, ChangeDetectionStrategy } from '@angular/core';

import { Instance, Parameter } from '@yamcs/client';

import { ActivatedRoute } from '@angular/router';

import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './SpaceSystemParametersTab.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SpaceSystemParametersTab {

  qualifiedName: string;

  instance: Instance;
  parameters$: Promise<Parameter[]>;

  constructor(route: ActivatedRoute, yamcs: YamcsService) {
    const parent = route.snapshot.parent!;
    this.qualifiedName = parent.paramMap.get('qualifiedName')!;
    this.parameters$ = yamcs.getInstanceClient()!.getParameters({
      namespace: this.qualifiedName
    });
    this.instance = yamcs.getInstance();
  }
}
