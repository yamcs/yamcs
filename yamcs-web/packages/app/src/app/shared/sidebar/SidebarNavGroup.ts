import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
  selector: 'app-sidebar-nav-group',
  templateUrl: './SidebarNavGroup.html',
  styleUrls: ['./SidebarNavGroup.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SidebarNavGroup {

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
