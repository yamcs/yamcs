import { Component, ChangeDetectionStrategy } from '@angular/core';

import { Instance, Algorithm } from '@yamcs/client';

import { ActivatedRoute } from '@angular/router';

import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './SpaceSystemAlgorithmsTab.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SpaceSystemAlgorithmsTab {

  qualifiedName: string;

  instance: Instance;
  algorithms$: Promise<Algorithm[]>;

  constructor(route: ActivatedRoute, yamcs: YamcsService) {
    const parent = route.snapshot.parent!;
    this.qualifiedName = parent.paramMap.get('qualifiedName')!;
    this.algorithms$ = yamcs.getInstanceClient()!.getAlgorithms({
      namespace: this.qualifiedName
    });
    this.instance = yamcs.getInstance();
  }
}
