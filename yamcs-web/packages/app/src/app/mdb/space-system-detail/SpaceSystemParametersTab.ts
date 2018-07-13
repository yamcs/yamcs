import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Instance, Parameter } from '@yamcs/client';
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
      namespace: this.qualifiedName,
      limit: 2500, // FIXME use proper pagination
    });
    this.instance = yamcs.getInstance();
  }
}
