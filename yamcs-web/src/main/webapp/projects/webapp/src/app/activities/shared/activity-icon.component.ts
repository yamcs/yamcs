import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Activity, WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-activity-icon',
  templateUrl: './activity-icon.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class ActivityIconComponent {

  activity = input.required<Activity>();
}
