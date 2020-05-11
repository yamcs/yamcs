import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { Alarm } from '../client';
import { YamcsService } from '../core/services/YamcsService';

@Component({
  selector: 'app-alarm-detail',
  templateUrl: './AlarmDetail.html',
  styleUrls: ['./AlarmDetail.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlarmDetail {

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
