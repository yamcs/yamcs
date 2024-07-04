
import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { MatIcon } from '@angular/material/icon';
import { MatListItem } from '@angular/material/list';

@Component({
  standalone: true,
  selector: 'ya-sidebar-nav-group',
  templateUrl: './sidebar-nav-group.component.html',
  styleUrl: './sidebar-nav-group.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatIcon,
    MatListItem
],
})
export class YaSidebarNavGroup {

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

  @Output()
  toggle = new EventEmitter<boolean>();
}
