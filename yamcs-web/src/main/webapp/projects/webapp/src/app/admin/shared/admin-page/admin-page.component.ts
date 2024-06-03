import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { ConfigService, User, WebappSdkModule, WebsiteConfig } from '@yamcs/webapp-sdk';
import { Subscription, filter } from 'rxjs';
import { AuthService } from '../../../core/services/AuthService';

@Component({
  standalone: true,
  templateUrl: './admin-page.component.html',
  styleUrl: './admin-page.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class AdminPageComponent implements OnDestroy {

  user: User;

  config: WebsiteConfig;

  userManagementActive = false;
  userManagementExpanded = false;
  rocksDbActive = false;
  rocksDbExpanded = false;

  private routerSubscription: Subscription;

  constructor(
    configService: ConfigService,
    authService: AuthService,
    router: Router,
  ) {
    this.config = configService.getConfig();
    this.user = authService.getUser()!;

    this.routerSubscription = router.events.pipe(
      filter(evt => evt instanceof NavigationEnd)
    ).subscribe((evt: any) => {
      const url = evt.url as string;
      this.collapseAllGroups();
      this.userManagementActive = false;
      this.rocksDbActive = false;
      if (url.match(/\/iam.*/)) {
        this.userManagementActive = true;
        this.userManagementExpanded = true;
      } else if (url.match(/\/rocksdb.*/)) {
        this.rocksDbActive = true;
        this.rocksDbExpanded = true;
      }
    });
  }

  private collapseAllGroups() {
    this.userManagementExpanded = false;
    this.rocksDbExpanded = false;
  }

  toggleUserManagementGroup() {
    const expanded = this.userManagementExpanded;
    this.collapseAllGroups();
    this.userManagementExpanded = !expanded;
  }

  toggleRocksDbGroup() {
    const expanded = this.rocksDbExpanded;
    this.collapseAllGroups();
    this.rocksDbExpanded = !expanded;
  }

  showRocksDbItem() {
    return this.user.hasSystemPrivilege('ControlArchiving');
  }

  showAccessControlItem() {
    return this.user.hasSystemPrivilege('ControlAccess');
  }

  showServicesItem() {
    return this.user.hasSystemPrivilege('ControlServices');
  }

  showSystemInfo() {
    return this.user.hasSystemPrivilege('ReadSystemInfo');
  }

  ngOnDestroy() {
    this.routerSubscription?.unsubscribe();
  }
}
