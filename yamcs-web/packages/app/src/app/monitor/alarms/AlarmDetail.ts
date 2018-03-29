import { Component, ChangeDetectionStrategy, Input } from '@angular/core';
import { Alarm } from '@yamcs/client';
import { DyDataSource } from '../../shared/widgets/DyDataSource';

@Component({
  selector: 'app-alarm-detail',
  templateUrl: './AlarmDetail.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlarmDetail {

  @Input()
  alarm: Alarm;

  @Input()
  plotDataSource: DyDataSource;
}
