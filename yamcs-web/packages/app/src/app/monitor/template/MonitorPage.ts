import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Instance } from '@yamcs/client';
import { BehaviorSubject } from 'rxjs';
import { AppConfig, APP_CONFIG, SidebarItem } from '../../core/config/AppConfig';
import { AuthService } from '../../core/services/AuthService';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './MonitorPage.html',
  styleUrls: ['./MonitorPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MonitorPage {

  instance$ = new BehaviorSubject<Instance | null>(null);

  extraItems: SidebarItem[];

  constructor(
    yamcs: YamcsService,
    @Inject(APP_CONFIG) appConfig: AppConfig,
    private authService: AuthService,
    route: ActivatedRoute,
  ) {
    const monitorConfig = appConfig.monitor || {};
    this.extraItems = monitorConfig.extraItems || [];

    route.queryParams.subscribe(() => {
      this.instance$.next(yamcs.getInstance());
    });
  }

  showEventsItem() {
    return this.authService.getUser()!.hasSystemPrivilege('ReadEvents');
  }
}
