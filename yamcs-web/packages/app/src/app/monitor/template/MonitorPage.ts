import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import { Instance } from '@yamcs/client';
import { AppConfig, APP_CONFIG, SidebarItem } from '../../core/config/AppConfig';
import { AuthService } from '../../core/services/AuthService';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './MonitorPage.html',
  styleUrls: ['./MonitorPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MonitorPage {

  instance: Instance;

  extraItems: SidebarItem[];

  constructor(
    yamcs: YamcsService,
    @Inject(APP_CONFIG) appConfig: AppConfig,
    private authService: AuthService,
  ) {
    this.instance = yamcs.getInstance();

    const monitorConfig = appConfig.monitor || {};
    this.extraItems = monitorConfig.extraItems || [];
  }

  showEventsItem() {
    return this.authService.getUser()!.hasSystemPrivilege('ReadEvents');
  }
}
