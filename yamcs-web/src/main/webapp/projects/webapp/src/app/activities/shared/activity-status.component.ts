import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Activity, WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-activity-status',
  templateUrl: './activity-status.component.html',
  styleUrl: './activity-status.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class ActivityStatusComponent {

  activity = input.required<Activity>();
}
