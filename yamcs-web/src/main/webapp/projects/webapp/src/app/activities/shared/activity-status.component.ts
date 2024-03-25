import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Activity } from '@yamcs/webapp-sdk';
import { SharedModule } from '../../shared/SharedModule';

@Component({
  standalone: true,
  selector: 'app-activity-status',
  templateUrl: './activity-status.component.html',
  styleUrl: './activity-status.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    SharedModule,
  ],
})
export class ActivityStatusComponent {

  activity = input.required<Activity>();
}
