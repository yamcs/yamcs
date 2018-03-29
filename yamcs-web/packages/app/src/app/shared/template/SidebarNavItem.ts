import { Component, ChangeDetectionStrategy, Input } from '@angular/core';

@Component({
  selector: 'app-sidebar-nav-item',
  templateUrl: './SidebarNavItem.html',
  styleUrls: ['./SidebarNavItem.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SidebarNavItem {

  @Input()
  routerLink: string;

  @Input()
  queryParams: {};
}
