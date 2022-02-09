import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { Router } from '@angular/router';
import * as dayjs from 'dayjs';
import { Dayjs } from 'dayjs';
import { YamcsService } from '../../../lib';
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
    'actions',
  ];

  constructor(
    private router: Router,
    readonly yamcs: YamcsService,
  ) { }

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

  showChart(alarm: Alarm) {
    const triggerIso = alarm.triggerTime;
    const clearIso = alarm.clearInfo?.clearTime;

    let start: Dayjs;
    let stop: Dayjs;
    if (clearIso) {
      start = dayjs.utc(triggerIso);
      stop = dayjs.utc(clearIso);
    } else {
      start = dayjs.utc(triggerIso);
      stop = dayjs.utc(triggerIso).add(1, 'hour');
    }

    this.router.navigate([
      '/telemetry/parameters',
      alarm.parameterDetail?.triggerValue.id.name,
      'chart'
    ], {
      queryParams: {
        c: this.yamcs.context,
        interval: 'CUSTOM',
        customStart: start.toISOString(),
        customStop: stop.toISOString(),
      },
    });
  }
}
