import { Component, ChangeDetectionStrategy, Input } from '@angular/core';
import { Alarm } from '@yamcs/client';

@Component({
  selector: 'app-alarm-detail',
  templateUrl: './AlarmDetail.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlarmDetail {

  @Input()
  alarm: Alarm;
}
