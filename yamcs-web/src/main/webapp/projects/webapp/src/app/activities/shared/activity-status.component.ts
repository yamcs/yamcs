import { Component, input } from '@angular/core';
import { Activity, WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-activity-status',
  templateUrl: './activity-status.component.html',
  styleUrl: './activity-status.component.css',
  imports: [WebappSdkModule],
})
export class ActivityStatusComponent {
  activity = input.required<Activity>();
}
