import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Observable } from 'rxjs/Observable';

import { Parameter } from '../../../yamcs-client';

import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './ParametersPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParametersPage {

  parameters$: Observable<Parameter[]>;

  constructor(yamcs: YamcsService) {
    this.parameters$ = yamcs.getSelectedInstance().getParameters();
  }
}
