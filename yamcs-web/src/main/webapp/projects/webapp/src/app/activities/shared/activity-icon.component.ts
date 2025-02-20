import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Activity, WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-activity-icon',
  templateUrl: './activity-icon.component.html',
  styleUrl: './activity-icon.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class ActivityIconComponent {

  activity = input.required<Activity>();
}
