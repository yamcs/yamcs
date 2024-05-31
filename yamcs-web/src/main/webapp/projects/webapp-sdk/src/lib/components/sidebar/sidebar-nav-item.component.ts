
import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { MatListItem } from '@angular/material/list';
import { RouterLink, RouterLinkActive } from '@angular/router';

@Component({
  standalone: true,
  selector: 'ya-sidebar-nav-item',
  templateUrl: './sidebar-nav-item.component.html',
  styleUrl: './sidebar-nav-item.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatListItem,
    RouterLink,
    RouterLinkActive
],
})
export class YaSidebarNavItem {

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
