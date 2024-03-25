import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { Alarm, YamcsService } from '@yamcs/webapp-sdk';
import { SharedModule } from '../../shared/SharedModule';

@Component({
  standalone: true,
  selector: 'app-alarm-detail',
  templateUrl: './alarm-detail.component.html',
  styleUrl: './alarm-detail.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    SharedModule,
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
