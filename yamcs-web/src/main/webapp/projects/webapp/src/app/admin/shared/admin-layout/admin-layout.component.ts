import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  computed,
  OnDestroy,
  signal,
  Signal,
  ViewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatSidenav } from '@angular/material/sidenav';
import { NavigationEnd, Router } from '@angular/router';
import {
  AuthService,
  ConfigService,
  PreferenceStore,
  User,
  WebappSdkModule,
  WebsiteConfig,
} from '@yamcs/webapp-sdk';
import { BehaviorSubject, filter, Subscription } from 'rxjs';
import { AppAppBaseToolbarLabel } from '../../../appbase/appbase-toolbar/appbase-toolbar-label.directive';
import { AppAppBaseToolbar } from '../../../appbase/appbase-toolbar/appbase-toolbar.component';
import { AppCollapseSidebar } from '../../../appbase/collapse-sidebar/collapse-sidebar.component';

@Component({
  templateUrl: './admin-layout.component.html',
  styleUrl: './admin-layout.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AppAppBaseToolbar,
    AppAppBaseToolbarLabel,
    AppCollapseSidebar,
    WebappSdkModule,
  ],
  host: {
    '[class.sidenav-hover]': 'sidenavHover()',
    '[class.mini]': 'collapsed()',
    '[class.no-transition]': '!pageLoaded()',
  },
})
export class AdminLayoutComponent implements AfterViewInit, OnDestroy {
  @ViewChild(MatSidenav)
  sidenav: MatSidenav;

  user: User;
  config: WebsiteConfig;

  userManagementActive = false;
  userManagementExpanded = false;
  rocksDbActive = false;
  rocksDbExpanded = false;

  pageLoaded = signal(false);
  collapsed: Signal<boolean>;
  sidenavHover = signal(false);
  miniSidebar$: BehaviorSubject<boolean>;
  collapseItem = computed(() => this.collapsed() && !this.sidenavHover());

  private routerSubscription: Subscription;

  constructor(
    configService: ConfigService,
    authService: AuthService,
    private preferenceStore: PreferenceStore,
    router: Router,
  ) {
    this.config = configService.getConfig();
    this.user = authService.getUser()!;
    this.miniSidebar$ = this.preferenceStore.getPreference$('miniSidebar');

    this.collapsed = toSignal(this.miniSidebar$, {
      requireSync: true,
    });

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

  ngAfterViewInit(): void {
    // Avoid sidebar FOUC when actually collapsed
    requestAnimationFrame(() => this.pageLoaded.set(true));
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

  toggleCollapse() {
    const mini = !this.miniSidebar$.value;
    this.preferenceStore.setValue('miniSidebar', mini);
    if (mini) {
      this.sidenavHover.set(false); // Close sidebar even if hovered
    }
  }

  collapseSidebarIfMini() {
    const mini = this.miniSidebar$.value;
    if (mini) {
      this.sidenavHover.set(false); // Close sidebar even if hovered
    }
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
