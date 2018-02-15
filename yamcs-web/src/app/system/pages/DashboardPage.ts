import { Component, ChangeDetectionStrategy } from '@angular/core';

import { YamcsService } from '../../core/services/YamcsService';
import { Observable } from 'rxjs/Observable';
import { GeneralInfo } from '../../../yamcs-client';

@Component({
  templateUrl: './DashboardPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardPage {

  info$: Observable<GeneralInfo>;

  constructor(yamcs: YamcsService) {
    this.info$ = yamcs.yamcsClient.getGeneralInfo();
  }
}
