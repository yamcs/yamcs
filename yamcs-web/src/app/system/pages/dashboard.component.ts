import { Component, ChangeDetectionStrategy } from '@angular/core';

import { YamcsService } from '../../core/services/yamcs.service';
import { Observable } from 'rxjs/Observable';
import { GeneralInfo } from '../../../yamcs-client';

@Component({
  templateUrl: './dashboard.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardPageComponent {

  info$: Observable<GeneralInfo>;

  constructor(yamcs: YamcsService) {
    this.info$ = yamcs.yamcsClient.getGeneralInfo();
  }
}
