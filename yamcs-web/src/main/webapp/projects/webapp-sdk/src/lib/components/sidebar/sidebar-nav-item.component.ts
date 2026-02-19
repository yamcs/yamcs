import {
  ChangeDetectionStrategy,
  Component,
  input,
  Input,
  OnDestroy,
  OnInit,
  signal,
} from '@angular/core';
import { MatIcon } from '@angular/material/icon';
import { NavigationEnd, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import { BaseComponent } from '../../abc/BaseComponent';

@Component({
  selector: 'ya-sidebar-nav-item',
  templateUrl: './sidebar-nav-item.component.html',
  styleUrl: './sidebar-nav-item.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatIcon, RouterLink],
})
export class YaSidebarNavItem
  extends BaseComponent
  implements OnInit, OnDestroy
{
  mini = input(false);
  routerLink = input.required<string>();
  activeWhen = input.required<string>();
  exact = input(false);

  @Input()
  icon: string;

  @Input()
  label: string;

  @Input()
  queryParams: {};

  @Input()
  subitem = false;

  @Input()
  color: string;

  @Input()
  backgroundColor: string;

  linkActive = signal(false);

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

      this.linkActive.set(urlWithoutParams === activeWhen);
    } else {
      this.linkActive.set(url.startsWith(this.activeWhen()));
    }
  }

  ngOnDestroy(): void {
    this.routerSubscription.unsubscribe();
  }
}
