import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatIcon } from '@angular/material/icon';

@Component({
  selector: 'ya-collapse-sidebar',
  templateUrl: './collapse-sidebar.component.html',
  styleUrl: './collapse-sidebar.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatIcon],
})
export class YaCollapseSidebar {
  collapsed = input.required<boolean>();
}
