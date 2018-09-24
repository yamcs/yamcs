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

  constructor(route: ActivatedRoute, private yamcs: YamcsService) {
    const parent = route.snapshot.parent!;
    this.qualifiedName = parent.paramMap.get('qualifiedName')!;
    this.instance = yamcs.getInstance();
    this.parameters$ = this.loadParameters();
  }

  // FIXME use proper pagination
  private async loadParameters() {
    const allParameters = [];
    let page = 1;
    const pageSize = 500;
    while (true) {
      const limit = pageSize + 1;
      const pos = (page - 1) * pageSize;
      const parameters = await this.yamcs.getInstanceClient()!.getParameters({
        namespace: this.qualifiedName,
        limit,
        pos,
      });
      if (parameters.length === limit) {
        allParameters.push(...parameters.slice(0, -1));
        page += 1;
      } else {
        allParameters.push(...parameters);
        break;
      }
    }
    return allParameters;
  }
}
