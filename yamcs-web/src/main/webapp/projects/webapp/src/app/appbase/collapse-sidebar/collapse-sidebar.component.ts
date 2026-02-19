import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-collapse-sidebar',
  templateUrl: './collapse-sidebar.component.html',
  styleUrl: './collapse-sidebar.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [WebappSdkModule],
})
export class AppCollapseSidebar {
  collapsed = input.required<boolean>();
}
