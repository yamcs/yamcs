import { ChangeDetectionStrategy, Component, ViewChild } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { MatTableDataSource } from '@angular/material/table';
import { ActivatedRoute, Router } from '@angular/router';
import * as dayjs from 'dayjs';
import { Dayjs } from 'dayjs';
import { MessageService } from '../../lib';
import { Alarm, GetAlarmsOptions } from '../client';
import { YamcsService } from '../core/services/YamcsService';
import { Option, Select } from '../shared/forms/Select';
import * as utils from '../shared/utils';
import { subtractDuration } from '../shared/utils';

@Component({
  templateUrl: './ClosedAlarmsPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClosedAlarmsPage {

  @ViewChild('intervalSelect')
  intervalSelect: Select;

  validStart: Date | null;
  validStop: Date | null;

  // Same as filter.interval but only updates after 'apply' in case of custom dates
  // This allows showing visual indicators for the visible data set before a custom
  // range is actually applied.
  appliedInterval: string;

  filterForm = new FormGroup({
    interval: new FormControl('NO_LIMIT'),
    customStart: new FormControl(null),
    customStop: new FormControl(null),
  });

  displayedColumns = [
    'severity',
    'triggerTime',
    'alarm',
    'type',
    'triggerValue',
    'duration',
    'actions',
  ];

  intervalOptions: Option[] = [
    { id: 'PT1H', label: 'Last hour' },
    { id: 'PT6H', label: 'Last 6 hours' },
    { id: 'P1D', label: 'Last 24 hours' },
    { id: 'NO_LIMIT', label: 'No limit' },
    { id: 'CUSTOM', label: 'Custom', group: true },
  ];

  dataSource = new MatTableDataSource<Alarm>();

  constructor(
    readonly yamcs: YamcsService,
    private router: Router,
    private route: ActivatedRoute,
    private messageService: MessageService,
  ) {
    this.initializeOptions();
    this.loadData();

    this.filterForm.get('interval')!.valueChanges.forEach(nextInterval => {
      if (nextInterval === 'CUSTOM') {
        const now = new Date();
        const customStart = this.validStart || now;
        const customStop = this.validStop || now;
        this.filterForm.get('customStart')!.setValue(utils.toISOString(customStart));
        this.filterForm.get('customStop')!.setValue(utils.toISOString(customStop));
      } else if (nextInterval === 'NO_LIMIT') {
        this.validStart = null;
        this.validStop = null;
        this.appliedInterval = nextInterval;
        this.loadData();
      } else {
        this.validStop = new Date();
        this.validStart = subtractDuration(this.validStop, nextInterval);
        this.appliedInterval = nextInterval;
        this.loadData();
      }
    });
  }

  private initializeOptions() {
    const queryParams = this.route.snapshot.queryParamMap;
    if (queryParams.has('interval')) {
      this.appliedInterval = queryParams.get('interval')!;
      this.filterForm.get('interval')!.setValue(this.appliedInterval);
      if (this.appliedInterval === 'CUSTOM') {
        const customStart = queryParams.get('customStart')!;
        this.filterForm.get('customStart')!.setValue(customStart);
        this.validStart = utils.toDate(customStart);
        const customStop = queryParams.get('customStop')!;
        this.filterForm.get('customStop')!.setValue(customStop);
        this.validStop = utils.toDate(customStop);
      } else if (this.appliedInterval === 'NO_LIMIT') {
        this.validStart = null;
        this.validStop = null;
      } else {
        this.validStop = new Date();
        this.validStart = subtractDuration(this.validStop, this.appliedInterval);
      }
    } else {
      this.appliedInterval = 'NO_LIMIT';
      this.validStop = null;
      this.validStart = null;
    }
  }

  jumpToNow() {
    this.filterForm.get('interval')!.setValue('NO_LIMIT');
  }

  applyCustomDates() {
    this.validStart = utils.toDate(this.filterForm.value['customStart']);
    this.validStop = utils.toDate(this.filterForm.value['customStop']);
    this.appliedInterval = 'CUSTOM';
    this.loadData();
  }

  loadData(next?: string) {
    this.updateURL();
    const options: GetAlarmsOptions = {
    };
    if (this.validStart) {
      options.start = this.validStart.toISOString();
    }
    if (this.validStop) {
      options.stop = this.validStop.toISOString();
    }

    this.yamcs.yamcsClient.getAlarms(this.yamcs.instance!, options)
      .then(alarms => this.dataSource.data = alarms)
      .catch(err => this.messageService.showError(err));
  }

  private updateURL() {
    this.router.navigate([], {
      replaceUrl: true,
      relativeTo: this.route,
      queryParams: {
        interval: this.appliedInterval,
        customStart: this.appliedInterval === 'CUSTOM' ? this.filterForm.value['customStart'] : null,
        customStop: this.appliedInterval === 'CUSTOM' ? this.filterForm.value['customStop'] : null,
      },
      queryParamsHandling: 'merge',
    });
  }

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
