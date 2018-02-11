import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Observable } from 'rxjs/Observable';

import { Parameter } from '../../../yamcs-client';

import { YamcsService } from '../../core/services/yamcs.service';

@Component({
  templateUrl: './parameters.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParametersPageComponent {

  parameters$: Observable<Parameter[]>;

  constructor(yamcs: YamcsService) {
    this.parameters$ = yamcs.getSelectedInstance().getParameters();
  }
}
