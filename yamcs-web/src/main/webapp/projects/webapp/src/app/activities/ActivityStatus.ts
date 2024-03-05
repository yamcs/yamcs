import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { Activity } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-activity-status',
  templateUrl: './ActivityStatus.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ActivityStatus {

  @Input()
  activity: Activity;
}
