import { Component, ChangeDetectionStrategy, Input } from '@angular/core';

@Component({
  selector: 'app-alarm-severity',
  templateUrl: './AlarmSeverityComponent.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlarmSeverityComponent {

  @Input()
  severity: string;
}
