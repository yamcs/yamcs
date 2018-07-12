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

  constructor(yamcs: YamcsService, title: Title) {
    title.setTitle('Parameters - Yamcs');
    this.instance = yamcs.getInstance();
    this.parameters$ = yamcs.getInstanceClient()!.getParameters({
      limit: 2500, // FIXME use proper pagination
    });
  }
}
