import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { Instance, Parameter } from '@yamcs/client';
import { YamcsService } from '../../core/services/YamcsService';


@Component({
  templateUrl: './ParametersPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParametersPage {

  instance: Instance;
  parameters$: Promise<Parameter[]>;

  constructor(private yamcs: YamcsService, title: Title) {
    title.setTitle('Parameters - Yamcs');
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
      const parameters = await this.yamcs.getInstanceClient()!.getParameters({ limit, pos });
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
