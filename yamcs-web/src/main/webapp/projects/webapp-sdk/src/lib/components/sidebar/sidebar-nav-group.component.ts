import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
  selector: 'ya-sidebar-nav-group',
  templateUrl: './sidebar-nav-group.component.html',
  styleUrl: './sidebar-nav-group.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SidebarNavGroupComponent {

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
