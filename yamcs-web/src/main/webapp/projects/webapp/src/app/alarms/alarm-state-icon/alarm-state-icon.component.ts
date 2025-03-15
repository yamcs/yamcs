import { Component, Input } from '@angular/core';
import { Alarm, WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-alarm-state-icon',
  templateUrl: './alarm-state-icon.component.html',
  imports: [WebappSdkModule],
})
export class AlarmStateIconComponent {
  @Input()
  alarm: Alarm;
}
