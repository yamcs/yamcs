import {
  ChangeDetectionStrategy,
  Component,
  ContentChildren,
  EventEmitter,
  inject,
  input,
  Output,
  QueryList,
} from '@angular/core';
import { MatIcon } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { BaseComponent } from '../../abc/BaseComponent';
import { YaSidenavItem } from './sidenav-item.component';
import { YaSidenav } from './sidenav.component';

@Component({
  selector: 'ya-sidenav-group',
  templateUrl: './sidenav-group.component.html',
  styleUrl: './sidenav-group.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatIcon, RouterLink],
  host: {
    class: 'ya-sidenav-group',
    '[class.mini]': 'mini()',
    '[class.active]': 'active()',
    '[class.expanded]': 'expanded()',
  },
})
export class YaSidenavGroup extends BaseComponent {
  label = input<string>();
  icon = input<string>();
  svgIcon = input<string>();
  active = input(false);
  expanded = input(false);

  private sidenav = inject(YaSidenav);
  mini = this.sidenav.collapseItem;

  @Output()
  toggle = new EventEmitter<boolean>();

  @ContentChildren(YaSidenavItem)
  items!: QueryList<YaSidenavItem>;

  clickGroup($event: MouseEvent) {
    this.toggle.emit(!this.expanded());
    $event.stopPropagation();
  }
}
