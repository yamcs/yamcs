import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { GeneralInfo, Parameter } from '@yamcs/client';
import { AuthService } from '../../core/services/AuthService';
import { YamcsService } from '../../core/services/YamcsService';
import { DyDataSource } from '../../shared/widgets/DyDataSource';


@Component({
  templateUrl: './DashboardPage.html',
  styleUrls: ['./DashboardPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardPage implements OnDestroy {

  info$: Promise<GeneralInfo>;

  jvmMemoryUsedParameter$: Promise<Parameter | null>;
  jvmTotalMemoryParameter$: Promise<Parameter | null>;
  jvmThreadCountParameter$: Promise<Parameter | null>;

  jvmMemoryUsedDataSource: DyDataSource;
  jvmThreadCountDataSource: DyDataSource;

  constructor(yamcs: YamcsService, title: Title, private authService: AuthService) {
    title.setTitle('Dashboard - Yamcs');
    this.info$ = yamcs.yamcsClient.getGeneralInfo();

    this.jvmMemoryUsedParameter$ = this.info$.then(info => {
      const jvmMemoryUsedId = `/yamcs/${info.serverId}/jvmMemoryUsed`;
      const jvmTotalMemoryId = `/yamcs/${info.serverId}/jvmTotalMemory`;
      if (authService.hasParameterPrivilege(jvmMemoryUsedId)) {
        this.jvmMemoryUsedDataSource = new DyDataSource(yamcs);
        this.jvmMemoryUsedDataSource.addParameter(jvmMemoryUsedId);
        this.jvmMemoryUsedDataSource.addParameter(jvmTotalMemoryId);
        this.jvmMemoryUsedDataSource.connectRealtime();
        return yamcs.getInstanceClient()!.getParameter(jvmMemoryUsedId);
      } else {
        return Promise.resolve(null);
      }
    });

    this.jvmTotalMemoryParameter$ = this.info$.then(info => {
      const jvmTotalMemoryId = `/yamcs/${info.serverId}/jvmTotalMemory`;
      if (authService.hasParameterPrivilege(jvmTotalMemoryId)) {
        return yamcs.getInstanceClient()!.getParameter(jvmTotalMemoryId);
      } else {
        return Promise.resolve(null);
      }
    });

    this.jvmThreadCountParameter$ = this.info$.then(info => {
      const jvmThreadCountId = `/yamcs/${info.serverId}/jvmThreadCount`;
      if (authService.hasParameterPrivilege(jvmThreadCountId)) {
        this.jvmThreadCountDataSource = new DyDataSource(yamcs);
        this.jvmThreadCountDataSource.addParameter(jvmThreadCountId);
        this.jvmThreadCountDataSource.connectRealtime();
        return yamcs.getInstanceClient()!.getParameter(jvmThreadCountId);
      } else {
        return Promise.resolve(null);
      }
    });
  }

  hasParameterPrivilege(parameter: string) {
    return this.authService.hasParameterPrivilege(parameter);
  }

  ngOnDestroy() {
    if (this.jvmMemoryUsedDataSource) {
      this.jvmMemoryUsedDataSource.disconnect();
    }
    if (this.jvmThreadCountDataSource) {
      this.jvmThreadCountDataSource.disconnect();
    }
  }
}
