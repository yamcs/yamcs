import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { Alarm, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { AlarmLevelComponent } from '../../shared/alarm-level/alarm-level.component';

@Component({
  standalone: true,
  selector: 'app-alarm-detail',
  templateUrl: './alarm-detail.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AlarmLevelComponent,
    WebappSdkModule,
  ],
})
export class AlarmDetailComponent {

  @Input()
  alarm: Alarm;

  @Input()
  mayControl = false;

  @Output()
  acknowledgeAlarm = new EventEmitter<Alarm>();

  @Output()
  shelveAlarm = new EventEmitter<Alarm>();

  @Output()
  unshelveAlarm = new EventEmitter<Alarm>();

  @Output()
  clearAlarm = new EventEmitter<Alarm>();

  constructor(readonly yamcs: YamcsService) {
  }
}
