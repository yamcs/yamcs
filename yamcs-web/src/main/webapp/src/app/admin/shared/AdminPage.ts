import { APP_BASE_HREF } from '@angular/common';
import { ChangeDetectionStrategy, Component, Inject, OnDestroy } from '@angular/core';
import { MatIconRegistry } from '@angular/material/icon';
import { DomSanitizer } from '@angular/platform-browser';
import { NavigationEnd, Router } from '@angular/router';
import { Observable, Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import { PreferenceStore } from '../../core/services/PreferenceStore';

@Component({
  templateUrl: './AdminPage.html',
  styleUrls: ['./AdminPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminPage implements OnDestroy {

  sidebar$: Observable<boolean>;

  userManagementActive = false;
  userManagementExpanded = false;
  rocksDbActive = false;
  rocksDbExpanded = false;

  private routerSubscription: Subscription;

  constructor(
    preferenceStore: PreferenceStore,
    iconRegistry: MatIconRegistry,
    sanitizer: DomSanitizer,
    router: Router,
    @Inject(APP_BASE_HREF) baseHref: string,
  ) {
    this.sidebar$ = preferenceStore.sidebar$;
    const resourceUrl = `${baseHref}static/rocksdb.svg`;
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

  ngOnDestroy() {
    if (this.routerSubscription) {
      this.routerSubscription.unsubscribe();
    }
  }
}
