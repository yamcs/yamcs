import { Component, ChangeDetectionStrategy } from '@angular/core';

import { Parameter, Instance } from '@yamcs/client';

import { YamcsService } from '../../core/services/YamcsService';
import { Title } from '@angular/platform-browser';

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
    this.parameters$ = yamcs.getInstanceClient()!.getParameters();
  }
}
