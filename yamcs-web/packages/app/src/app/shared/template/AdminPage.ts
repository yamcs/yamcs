import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
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

  rocksDbActive = false;
  rocksDbExpanded = false;

  private routerSubscription: Subscription;

  constructor(
    preferenceStore: PreferenceStore,
    iconRegistry: MatIconRegistry,
    sanitizer: DomSanitizer,
    router: Router,
  ) {
    this.sidebar$ = preferenceStore.sidebar$;
    iconRegistry.addSvgIcon('rocksdb', sanitizer.bypassSecurityTrustResourceUrl('/static/assets/rocksdb.svg'));

    this.routerSubscription = router.events.pipe(
      filter(evt => evt instanceof NavigationEnd)
    ).subscribe((evt: any) => {
      const url = evt.url as string;
      this.collapseAllGroups();
      if (url.match(/\/rocksdb.*/)) {
        this.rocksDbActive = true;
        this.rocksDbExpanded = true;
      }
    });
  }

  private collapseAllGroups() {
    this.rocksDbExpanded = false;
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
