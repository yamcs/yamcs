import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  OnDestroy,
  OnInit,
  signal,
} from '@angular/core';
import { MatIcon } from '@angular/material/icon';
import { NavigationEnd, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import { BaseComponent } from '../../abc/BaseComponent';
import { YaSidenavGroup } from './sidenav-group.component';
import { YaSidenav } from './sidenav.component';

@Component({
  selector: 'ya-sidenav-item',
  templateUrl: './sidenav-item.component.html',
  styleUrl: './sidenav-item.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ya-sidenav-item',
    '[class.mini]': 'mini()',
    '[class.subitem]': 'subitem()',
    '[class.active]': 'active()',
  },
  imports: [MatIcon, RouterLink],
})
export class YaSidenavItem extends BaseComponent implements OnInit, OnDestroy {
  icon = input<string>();
  label = input<string>();
  color = input<string>();
  backgroundColor = input<string>();

  routerLink = input.required<string>();
  queryParams = input<string>();
  activeWhen = input.required<string>();
  exact = input(false);

  private sidenav = inject(YaSidenav);
  mini = this.sidenav.collapseItem;

  private sidenavGroup = inject(YaSidenavGroup, { optional: true });
  subitem = computed(() => !!this.sidenavGroup);

  active = signal(false);

  private routerSubscription: Subscription;

  ngOnInit(): void {
    this.checkLinkActive(this.router.url);
    this.routerSubscription = this.router.events
      .pipe(filter((evt) => evt instanceof NavigationEnd))
      .subscribe((evt) => {
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
        urlWithoutParams = urlWithoutParams.substring(
          0,
          urlWithoutParams.length - 1,
        );
      }

      this.active.set(urlWithoutParams === activeWhen);
    } else {
      this.active.set(url.startsWith(this.activeWhen()));
    }
  }

  ngOnDestroy(): void {
    this.routerSubscription.unsubscribe();
  }
}
