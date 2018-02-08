import { Component, ChangeDetectionStrategy, Input } from '@angular/core';

@Component({
  selector: 'app-sidebar-nav-item',
  templateUrl: './sidebar-nav-item.component.html',
  styleUrls: ['./sidebar-nav-item.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SidebarNavItemComponent {

  @Input()
  routerLink: string;

  @Input()
  queryParams: {};
}
