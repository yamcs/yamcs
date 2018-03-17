import { Component, ChangeDetectionStrategy } from '@angular/core';

import { YamcsService } from '../../core/services/YamcsService';
import { Parameter } from '@yamcs/client';
import { GeneralInfo } from '@yamcs/client';
import { Title } from '@angular/platform-browser';

@Component({
  templateUrl: './DashboardPage.html',
  styleUrls: ['./DashboardPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardPage {

  info$: Promise<GeneralInfo>;

  jvmMemoryUsedParameter$: Promise<Parameter>;
  jvmTotalMemoryParameter$: Promise<Parameter>;
  jvmThreadCountParameter$: Promise<Parameter>;

  constructor(yamcs: YamcsService, title: Title) {
    title.setTitle('Dashboard - Yamcs');
    this.info$ = yamcs.yamcsClient.getGeneralInfo();

    this.jvmMemoryUsedParameter$ = this.info$.then(info => {
      const jvmMemoryUsedId = `/yamcs/${info.serverId}/jvmMemoryUsed`;
       return yamcs.getSelectedInstance().getParameter(jvmMemoryUsedId);
    });

    this.jvmTotalMemoryParameter$ = this.info$.then(info => {
      const jvmTotalMemoryId = `/yamcs/${info.serverId}/jvmTotalMemory`;
      return yamcs.getSelectedInstance().getParameter(jvmTotalMemoryId);
    });

    this.jvmThreadCountParameter$ = this.info$.then(info => {
      const jvmThreadCountId = `/yamcs/${info.serverId}/jvmThreadCount`;
      return yamcs.getSelectedInstance().getParameter(jvmThreadCountId);
    });
  }
}
