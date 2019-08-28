import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { Parameter } from '@yamcs/client';
import { AuthService } from '../core/services/AuthService';
import { ConfigService } from '../core/services/ConfigService';
import { Synchronizer } from '../core/services/Synchronizer';
import { YamcsService } from '../core/services/YamcsService';
import { DyDataSource } from '../shared/widgets/DyDataSource';


@Component({
  templateUrl: './SystemPage.html',
  styleUrls: ['./SystemPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SystemPage implements OnDestroy {

  jvmMemoryUsedParameter$: Promise<Parameter | null>;
  jvmTotalMemoryParameter$: Promise<Parameter | null>;
  jvmThreadCountParameter$: Promise<Parameter | null>;

  jvmMemoryUsedDataSource: DyDataSource;
  jvmThreadCountDataSource: DyDataSource;

  constructor(
    yamcs: YamcsService,
    title: Title,
    private authService: AuthService,
    synchronizer: Synchronizer,
    configService: ConfigService,
  ) {
    title.setTitle('System');
    const instanceClient = yamcs.getInstanceClient()!;
    const serverId = configService.getServerId();
    this.jvmMemoryUsedParameter$ = instanceClient.getParameter(`/yamcs/${serverId}/jvmMemoryUsed`);
    this.jvmTotalMemoryParameter$ = instanceClient.getParameter(`/yamcs/${serverId}/jvmTotalMemory`);
    this.jvmThreadCountParameter$ = instanceClient.getParameter(`/yamcs/${serverId}/jvmThreadCount`);

    Promise.all([this.jvmMemoryUsedParameter$, this.jvmTotalMemoryParameter$]).then(results => {
      if (results[0] && results[1]) {
        this.jvmMemoryUsedDataSource = new DyDataSource(yamcs, synchronizer);
        this.jvmMemoryUsedDataSource.addParameter(results[0]!, results[1]!);
      }
    });

    this.jvmThreadCountParameter$.then(parameter => {
      if (parameter) {
        this.jvmThreadCountDataSource = new DyDataSource(yamcs, synchronizer);
        this.jvmThreadCountDataSource.addParameter(parameter);
      }
    });
  }

  hasParameterPrivilege(parameter: string) {
    return this.authService.getUser()!.hasObjectPrivilege('ReadParameter', parameter);
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
