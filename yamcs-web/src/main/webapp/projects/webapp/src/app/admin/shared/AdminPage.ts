import { APP_BASE_HREF } from '@angular/common';
import { ChangeDetectionStrategy, Component, Inject, OnDestroy } from '@angular/core';
import { MatIconRegistry } from '@angular/material/icon';
import { DomSanitizer } from '@angular/platform-browser';
import { NavigationEnd, Router } from '@angular/router';
import { ConfigService, PreferenceStore, User, WebsiteConfig } from '@yamcs/webapp-sdk';
import { Subscription, filter } from 'rxjs';
import { AuthService } from '../../core/services/AuthService';

@Component({
  templateUrl: './AdminPage.html',
  styleUrls: ['./AdminPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminPage implements OnDestroy {

  user: User;

  config: WebsiteConfig;

  userManagementActive = false;
  userManagementExpanded = false;
  rocksDbActive = false;
  rocksDbExpanded = false;

  private routerSubscription: Subscription;

  constructor(
    preferenceStore: PreferenceStore,
    configService: ConfigService,
    authService: AuthService,
    iconRegistry: MatIconRegistry,
    sanitizer: DomSanitizer,
    router: Router,
    @Inject(APP_BASE_HREF) baseHref: string,
  ) {
    this.config = configService.getConfig();
    this.user = authService.getUser()!;
    const resourceUrl = `${baseHref}rocksdb.svg`;
    const safeResourceUrl = sanitizer.bypassSecurityTrustResourceUrl(resourceUrl);
    iconRegistry.addSvgIcon('rocksdb', safeResourceUrl);

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
