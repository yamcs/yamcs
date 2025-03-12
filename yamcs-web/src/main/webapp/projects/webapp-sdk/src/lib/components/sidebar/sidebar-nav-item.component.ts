
import { ChangeDetectionStrategy, Component, inject, input, Input, OnDestroy, OnInit, signal } from '@angular/core';
import { MatListItem } from '@angular/material/list';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';

@Component({
  selector: 'ya-sidebar-nav-item',
  templateUrl: './sidebar-nav-item.component.html',
  styleUrl: './sidebar-nav-item.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatListItem,
    RouterLink,
  ],
})
export class YaSidebarNavItem implements OnInit, OnDestroy {
  router = inject(Router);

  routerLink = input.required<string>();
  activeWhen = input.required<string>();
  exact = input(false);

  @Input()
  queryParams: {};

  @Input()
  subitem = false;

  @Input()
  color: string;

  linkActive = signal(false);

  private routerSubscription: Subscription;

  ngOnInit(): void {
    this.checkLinkActive(this.router.url);
    this.routerSubscription = this.router.events.pipe(
      filter(evt => evt instanceof NavigationEnd),
    ).subscribe(evt => {
      this.checkLinkActive(evt.url);
    });
  }

  private checkLinkActive(url: string) {
    if (this.exact()) {
      let activeWhen = this.activeWhen();
      if (activeWhen.endsWith('/')) {
        activeWhen = activeWhen.substring(0, activeWhen.length - 1);
      }

      const urlTree = this.router.parseUrl(this.router.url);
      urlTree.queryParams = {};
      urlTree.fragment = null;
      let urlWithoutParams = urlTree.toString();
      if (urlWithoutParams.endsWith('/')) {
        urlWithoutParams = urlWithoutParams.substring(0, urlWithoutParams.length - 1);
      }

      this.linkActive.set(urlWithoutParams === activeWhen);
    } else {
      this.linkActive.set(url.startsWith(this.activeWhen()));
    }
  }

  ngOnDestroy(): void {
    this.routerSubscription.unsubscribe();
  }
}
