import {
  ChangeDetectionStrategy,
  Component,
  inject,
  OnDestroy,
  ViewChild,
} from '@angular/core';
import { MatSidenav } from '@angular/material/sidenav';
import { NavigationEnd, Router } from '@angular/router';
import {
  AppearanceService,
  AuthService,
  User,
  WebappSdkModule,
} from '@yamcs/webapp-sdk';
import { filter, Subscription } from 'rxjs';
import { AppAppBaseToolbarLabel } from '../../../appbase/appbase-toolbar/appbase-toolbar-label.directive';
import { AppAppBaseToolbar } from '../../../appbase/appbase-toolbar/appbase-toolbar.component';

@Component({
  templateUrl: './admin-layout.component.html',
  styleUrl: './admin-layout.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AppAppBaseToolbar, AppAppBaseToolbarLabel, WebappSdkModule],
  host: {
    '[class.collapsed]': 'isCollapsed()',
  },
})
export class AdminLayoutComponent implements OnDestroy {
  private appearanceService = inject(AppearanceService);

  // Whether to collapse the sidenav
  isCollapsed = this.appearanceService.isCollapsed;

  @ViewChild(MatSidenav)
  sidenav: MatSidenav;

  user: User;

  userManagementActive = false;
  userManagementExpanded = false;
  rocksDbActive = false;
  rocksDbExpanded = false;

  private routerSubscription: Subscription;

  constructor(authService: AuthService, router: Router) {
    this.user = authService.getUser()!;

    this.routerSubscription = router.events
      .pipe(filter((evt) => evt instanceof NavigationEnd))
      .subscribe((evt: any) => {
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
