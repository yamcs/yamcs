import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { slideDownAnimation } from '../../animations';

@Component({
  selector: 'app-sidebar-nav-group',
  templateUrl: './SidebarNavGroup.html',
  styleUrls: ['./SidebarNavGroup.css'],
  animations: [slideDownAnimation],
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
