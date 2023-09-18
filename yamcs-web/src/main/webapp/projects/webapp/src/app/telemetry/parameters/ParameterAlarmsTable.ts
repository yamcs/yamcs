import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { Router } from '@angular/router';
import { Alarm, YamcsService, utils } from '@yamcs/webapp-sdk';
import * as dayjs from 'dayjs';
import { Dayjs } from 'dayjs';
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
    'start',
    'stop',
    'duration',
    'triggerValue',
    'violations',
    'actions',
  ];

  constructor(
    private router: Router,
    readonly yamcs: YamcsService,
  ) { }

  durationFor(alarm: Alarm) {
    if (!alarm.updateTime) {
      return undefined;
    }
    return utils.toDate(alarm.updateTime).getTime() - utils.toDate(alarm.triggerTime).getTime();
  }

  showChart(alarm: Alarm) {
    const startIso = alarm.triggerTime;
    const stopIso = alarm.updateTime || alarm.clearInfo?.clearTime;

    let start: Dayjs;
    let stop: Dayjs;
    if (stopIso) {
      start = dayjs.utc(startIso);
      stop = dayjs.utc(stopIso);
    } else {
      start = dayjs.utc(startIso);
      stop = dayjs.utc(stopIso).add(1, 'hour');
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

  showData(alarm: Alarm) {
    const startIso = alarm.triggerTime;
    const stopIso = alarm.updateTime || alarm.clearInfo?.clearTime;

    let start: Dayjs;
    let stop: Dayjs;
    if (stopIso) {
      start = dayjs.utc(startIso);
      stop = dayjs.utc(stopIso);
    } else {
      start = dayjs.utc(startIso);
      stop = dayjs.utc(stopIso).add(1, 'hour');
    }

    this.router.navigate([
      '/telemetry/parameters',
      alarm.parameterDetail?.triggerValue.id.name,
      'data'
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
