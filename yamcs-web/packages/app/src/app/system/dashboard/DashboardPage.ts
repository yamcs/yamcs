import { Component, ChangeDetectionStrategy, OnDestroy } from '@angular/core';

import { YamcsService } from '../../core/services/YamcsService';
import { Parameter } from '@yamcs/client';
import { GeneralInfo } from '@yamcs/client';
import { Title } from '@angular/platform-browser';
import { DyDataSource } from '../../shared/widgets/DyDataSource';

@Component({
  templateUrl: './DashboardPage.html',
  styleUrls: ['./DashboardPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardPage implements OnDestroy {

  info$: Promise<GeneralInfo>;

  jvmMemoryUsedParameter$: Promise<Parameter>;
  jvmTotalMemoryParameter$: Promise<Parameter>;
  jvmThreadCountParameter$: Promise<Parameter>;

  jvmMemoryUsedDataSource: DyDataSource;
  jvmTotalMemoryDataSource: DyDataSource;
  jvmThreadCountDataSource: DyDataSource;

  constructor(yamcs: YamcsService, title: Title) {
    title.setTitle('Dashboard - Yamcs');
    this.info$ = yamcs.yamcsClient.getGeneralInfo();

    this.jvmMemoryUsedParameter$ = this.info$.then(info => {
      const jvmMemoryUsedId = `/yamcs/${info.serverId}/jvmMemoryUsed`;
      this.jvmMemoryUsedDataSource = new DyDataSource(yamcs, jvmMemoryUsedId);
      this.jvmMemoryUsedDataSource.connectRealtime();
      return yamcs.getSelectedInstance().getParameter(jvmMemoryUsedId);
    });

    this.jvmTotalMemoryParameter$ = this.info$.then(info => {
      const jvmTotalMemoryId = `/yamcs/${info.serverId}/jvmTotalMemory`;
      this.jvmTotalMemoryDataSource = new DyDataSource(yamcs, jvmTotalMemoryId);
      this.jvmTotalMemoryDataSource.connectRealtime();
      return yamcs.getSelectedInstance().getParameter(jvmTotalMemoryId);
    });

    this.jvmThreadCountParameter$ = this.info$.then(info => {
      const jvmThreadCountId = `/yamcs/${info.serverId}/jvmThreadCount`;
      this.jvmThreadCountDataSource = new DyDataSource(yamcs, jvmThreadCountId);
      this.jvmThreadCountDataSource.connectRealtime();
      return yamcs.getSelectedInstance().getParameter(jvmThreadCountId);
    });
  }

  ngOnDestroy() {
    if (this.jvmMemoryUsedDataSource) {
      this.jvmMemoryUsedDataSource.disconnect();
    }
    if (this.jvmTotalMemoryDataSource) {
      this.jvmTotalMemoryDataSource.disconnect();
    }
    if (this.jvmThreadCountDataSource) {
      this.jvmThreadCountDataSource.disconnect();
    }
  }
}
