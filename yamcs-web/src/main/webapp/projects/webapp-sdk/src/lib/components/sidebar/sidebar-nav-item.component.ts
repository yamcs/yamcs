import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'ya-sidebar-nav-item',
  templateUrl: './sidebar-nav-item.component.html',
  styleUrls: ['./sidebar-nav-item.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SidebarNavItemComponent {

  @Input()
  routerLink: string;

  @Input()
  queryParams: {};

  @Input()
  exact = false;

  @Input()
  subitem = false;

  @Input()
  color: string;
}
