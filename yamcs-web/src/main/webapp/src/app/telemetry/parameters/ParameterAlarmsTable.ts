import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { Alarm } from '../../client';
import * as utils from '../../shared/utils';
import { ParameterAlarmsDataSource } from './ParameterAlarmsDataSource';

@Component({
  selector: 'app-parameter-alarms-table',
  templateUrl: './ParameterAlarmsTable.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParameterAlarmsTable {

  @Input()
  dataSource: ParameterAlarmsDataSource;

  displayedColumns = [
    'severity',
    'triggerTime',
    'triggerValue',
    'duration',
  ];

  printDuration(alarm: Alarm) {
    if (alarm.clearInfo) {
      const t1 = utils.toDate(alarm.triggerTime).getTime();
      const t2 = utils.toDate(alarm.clearInfo.clearTime).getTime();
      const totalSeconds = Math.floor((t2 - t1) / 1000);
      let interval = Math.floor(totalSeconds / 31536000);

      if (interval > 1) {
        return interval + ' years';
      }
      interval = Math.floor(totalSeconds / 2592000);
      if (interval > 1) {
        return interval + ' months';
      }
      interval = Math.floor(totalSeconds / 86400);
      if (interval > 1) {
        return interval + ' days';
      }
      interval = Math.floor(totalSeconds / 3600);
      if (interval > 1) {
        return interval + ' hours';
      }
      interval = Math.floor(totalSeconds / 60);
      if (interval > 1) {
        return interval + ' minutes';
      }

      interval = Math.floor(totalSeconds);
      if (interval > 60) {
        return 'about a minute';
      } else {
        return 'less than a minute';
      }
    } else {
      return null;
    }
  }
}
