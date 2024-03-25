import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Activity } from '@yamcs/webapp-sdk';
import { SharedModule } from '../../shared/SharedModule';

@Component({
  standalone: true,
  selector: 'app-activity-icon',
  templateUrl: './activity-icon.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    SharedModule,
  ],
})
export class ActivityIconComponent {

  activity = input.required<Activity>();
}
