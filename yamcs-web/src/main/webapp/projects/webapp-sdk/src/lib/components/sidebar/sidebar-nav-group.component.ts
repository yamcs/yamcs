import {
  ChangeDetectionStrategy,
  Component,
  ContentChildren,
  EventEmitter,
  input,
  Input,
  Output,
  QueryList,
} from '@angular/core';
import { MatIcon } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { BaseComponent } from '../../abc/BaseComponent';
import { YaSidebarNavItem } from './sidebar-nav-item.component';

@Component({
  selector: 'ya-sidebar-nav-group',
  templateUrl: './sidebar-nav-group.component.html',
  styleUrl: './sidebar-nav-group.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatIcon, RouterLink],
})
export class YaSidebarNavGroup extends BaseComponent {
  @Input()
  label: string;

  @Input()
  icon: string;

  @Input()
  svgIcon: string;

  @Input()
  active = false;

  @Input()
  expanded = false;

  mini = input(false);

  @Output()
  toggle = new EventEmitter<boolean>();

  @ContentChildren(YaSidebarNavItem)
  items!: QueryList<YaSidebarNavItem>;

  clickGroup($event: MouseEvent) {
    this.toggle.emit(!this.expanded);
    $event.stopPropagation();
  }
}
