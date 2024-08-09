import { ChangeDetectionStrategy, Component } from '@angular/core';
import { UntypedFormControl, UntypedFormGroup } from '@angular/forms';
import { MatTableDataSource } from '@angular/material/table';
import { ActivatedRoute, Router } from '@angular/router';
import { Alarm, GetAlarmsOptions, MessageService, WebappSdkModule, YaSelectOption, YamcsService, utils } from '@yamcs/webapp-sdk';
import { addHours } from 'date-fns';
import { AlarmLevelComponent } from '../../shared/alarm-level/alarm-level.component';
import { InstancePageTemplateComponent } from '../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../shared/instance-toolbar/instance-toolbar.component';
import { AlarmsPageTabsComponent } from '../alarms-page-tabs/alarms-page-tabs.component';

@Component({
  standalone: true,
  templateUrl: './alarm-history.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AlarmLevelComponent,
    AlarmsPageTabsComponent,
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    WebappSdkModule,
  ],
})
export class AlarmHistoryComponent {

  validStart: Date | null;
  validStop: Date | null;

  // Same as filter.interval but only updates after 'apply' in case of custom dates
  // This allows showing visual indicators for the visible data set before a custom
  // range is actually applied.
  appliedInterval: string;

  filterForm = new UntypedFormGroup({
    interval: new UntypedFormControl('NO_LIMIT'),
    customStart: new UntypedFormControl(null),
    customStop: new UntypedFormControl(null),
  });

  displayedColumns = [
    'severity',
    'start',
    'stop',
    'duration',
    'alarm',
    'type',
    'triggerValue',
    'violations',
    'actions',
  ];

  intervalOptions: YaSelectOption[] = [
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
        this.validStart = utils.subtractDuration(this.validStop, nextInterval);
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
        this.validStart = utils.subtractDuration(this.validStop, this.appliedInterval);
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
      'chart',
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
      'data',
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
