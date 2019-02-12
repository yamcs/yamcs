import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Instance } from '@yamcs/client';
import { BehaviorSubject } from 'rxjs';
import { AppConfig, APP_CONFIG, SidebarItem } from '../../core/config/AppConfig';
import { AuthService } from '../../core/services/AuthService';
import { YamcsService } from '../../core/services/YamcsService';
import { User } from '../../shared/User';

@Component({
  templateUrl: './InstancePage.html',
  styleUrls: ['./InstancePage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InstancePage {

  instance$ = new BehaviorSubject<Instance | null>(null);

  user: User;

  extraItems: SidebarItem[];

  monitoringExpanded = false;
  commandingExpanded = false;
  mdbExpanded = false;
  systemExpanded = false;

  constructor(
    yamcs: YamcsService,
    @Inject(APP_CONFIG) appConfig: AppConfig,
    authService: AuthService,
    route: ActivatedRoute,
  ) {
    const monitorConfig = appConfig.monitor || {};
    this.extraItems = monitorConfig.extraItems || [];

    this.user = authService.getUser()!;

    route.queryParams.subscribe(() => {
      this.instance$.next(yamcs.getInstance());
    });
  }

  showEventsItem() {
    return this.user.hasSystemPrivilege('ReadEvents');
  }

  showServicesItem() {
    return this.user.hasSystemPrivilege('ControlServices');
  }

  showTablesItem() {
    return this.user.hasSystemPrivilege('ReadTables');
  }

  showStreamsItem() {
    const objectPrivileges = this.user.getObjectPrivileges();
    for (const priv of objectPrivileges) {
      if (priv.type === 'Stream') {
        return true;
      }
    }
    return this.user.isSuperuser();
  }
}
