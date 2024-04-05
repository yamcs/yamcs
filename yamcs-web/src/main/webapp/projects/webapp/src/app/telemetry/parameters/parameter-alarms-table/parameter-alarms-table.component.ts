import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { Router } from '@angular/router';
import { Alarm, WebappSdkModule, YamcsService, utils } from '@yamcs/webapp-sdk';
import { addHours } from 'date-fns';
import { AlarmLevelComponent } from '../../../shared/alarm-level/alarm-level.component';
import { ParameterAlarmsDataSource } from './parameter-alarms.datasource';

@Component({
  standalone: true,
  selector: 'app-parameter-alarms-table',
  templateUrl: './parameter-alarms-table.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AlarmLevelComponent,
    WebappSdkModule,
  ],
})
export class ParameterAlarmsTableComponent {

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

    let start: string;
    let stop: string;
    if (stopIso) {
      start = startIso;
      stop = stopIso;
    } else {
      start = startIso;
      stop = addHours(utils.toDate(startIso), 1).toISOString();
    }

    this.router.navigate([
      '/telemetry/parameters' + alarm.parameterDetail?.triggerValue.id.name,
      '-',
      'chart'
    ], {
      queryParams: {
        c: this.yamcs.context,
        interval: 'CUSTOM',
        customStart: start,
        customStop: stop,
      },
    });
  }

  showData(alarm: Alarm) {
    const startIso = alarm.triggerTime;
    const stopIso = alarm.updateTime || alarm.clearInfo?.clearTime;

    let start: string;
    let stop: string;
    if (stopIso) {
      start = startIso;
      stop = stopIso;
    } else {
      start = startIso;
      stop = addHours(utils.toDate(startIso), 1).toISOString();
    }

    this.router.navigate([
      '/telemetry/parameters' + alarm.parameterDetail?.triggerValue.id.name,
      '-',
      'data'
    ], {
      queryParams: {
        c: this.yamcs.context,
        interval: 'CUSTOM',
        customStart: start,
        customStop: stop,
      },
    });
  }
}
