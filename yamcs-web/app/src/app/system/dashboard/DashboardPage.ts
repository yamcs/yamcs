import { Component, ChangeDetectionStrategy } from '@angular/core';

import { YamcsService } from '../../core/services/YamcsService';
import { Observable } from 'rxjs/Observable';
import { GeneralInfo, Parameter } from '../../../yamcs-client';
import { switchMap } from 'rxjs/operators';

@Component({
  templateUrl: './DashboardPage.html',
  styleUrls: ['./DashboardPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardPage {

  info$: Observable<GeneralInfo>;

  jvmMemoryUsedParameter$: Observable<Parameter>;
  jvmTotalMemoryParameter$: Observable<Parameter>;
  jvmThreadCountParameter$: Observable<Parameter>;

  constructor(yamcs: YamcsService) {
    this.info$ = yamcs.yamcsClient.getGeneralInfo();

    this.jvmMemoryUsedParameter$ = this.info$.pipe(
      switchMap(info => {
        const jvmMemoryUsedId = `/yamcs/${info.serverId}/jvmMemoryUsed`;
        return yamcs.getSelectedInstance().getParameter(jvmMemoryUsedId);
      })
    );

    this.jvmTotalMemoryParameter$ = this.info$.pipe(
      switchMap(info => {
        const jvmTotalMemoryId = `/yamcs/${info.serverId}/jvmTotalMemory`;
        return yamcs.getSelectedInstance().getParameter(jvmTotalMemoryId);
      })
    );

    this.jvmThreadCountParameter$ = this.info$.pipe(
      switchMap(info => {
        const jvmThreadCountId = `/yamcs/${info.serverId}/jvmThreadCount`;
        return yamcs.getSelectedInstance().getParameter(jvmThreadCountId);
      })
    );
  }
}
