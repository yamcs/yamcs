import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, input } from '@angular/core';
import { UntypedFormControl, UntypedFormGroup } from '@angular/forms';
import { GetAlarmsOptions, WebappSdkModule, YaSelectOption, YamcsService, utils } from '@yamcs/webapp-sdk';
import { ParameterAlarmsTableComponent } from '../parameter-alarms-table/parameter-alarms-table.component';
import { ParameterAlarmsDataSource } from '../parameter-alarms-table/parameter-alarms.datasource';

const defaultInterval = 'P1M';

@Component({
  standalone: true,
  templateUrl: './parameter-alarms-tab.component.html',
  styleUrl: './parameter-alarms-tab.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ParameterAlarmsTableComponent,
    WebappSdkModule,
  ],
})
export class ParameterAlarmsTabComponent implements OnInit, OnDestroy {

  qualifiedName = input.required<string>({ alias: 'parameter' });

  intervalOptions: YaSelectOption[] = [
    { id: 'P1M', label: 'Last Month' },
    { id: 'P1Y', label: 'Last Year' },
    { id: 'NO_LIMIT', label: 'No Limit' },
    { id: 'CUSTOM', label: 'Custom' },
  ];

  validStart: Date | null;
  validStop: Date | null;

  // Same as filter.interval but only updates after 'apply' in case of custom dates
  // This allows showing visual indicators for the visible data set before a custom
  // range is actually applied.
  appliedInterval: string;

  filter = new UntypedFormGroup({
    interval: new UntypedFormControl(defaultInterval),
    customStart: new UntypedFormControl(null),
    customStop: new UntypedFormControl(null),
  });

  dataSource: ParameterAlarmsDataSource;

  constructor(readonly yamcs: YamcsService) {
  }

  ngOnInit() {
    const qualifiedName = this.qualifiedName();
    this.dataSource = new ParameterAlarmsDataSource(this.yamcs, qualifiedName);

    this.validStop = this.yamcs.getMissionTime();
    this.validStart = utils.subtractDuration(this.validStop, defaultInterval);
    this.appliedInterval = defaultInterval;
    this.loadData();

    this.filter.get('interval')!.valueChanges.forEach(nextInterval => {
      if (nextInterval === 'CUSTOM') {
        const customStart = this.validStart || this.yamcs.getMissionTime();
        const customStop = this.validStop || this.yamcs.getMissionTime();
        this.filter.get('customStart')!.setValue(utils.toISOString(customStart));
        this.filter.get('customStop')!.setValue(utils.toISOString(customStop));
      } else if (nextInterval === 'NO_LIMIT') {
        this.validStart = null;
        this.validStop = null;
        this.appliedInterval = nextInterval;
        this.loadData();
      } else {
        this.validStop = this.yamcs.getMissionTime();
        this.validStart = utils.subtractDuration(this.validStop, nextInterval);
        this.appliedInterval = nextInterval;
        this.loadData();
      }
    });
  }

  jumpToNow() {
    const interval = this.filter.value['interval'];
    if (interval === 'NO_LIMIT') {
      // NO_LIMIT may include future data under erratic conditions. Reverting
      // to the default interval is more in line with the wording 'jump to now'.
      this.filter.get('interval')!.setValue(defaultInterval);
    } else {
      this.validStop = this.yamcs.getMissionTime();
      this.validStart = utils.subtractDuration(this.validStop, interval);
      this.loadData();
    }
  }

  applyCustomDates() {
    this.validStart = utils.toDate(this.filter.value['customStart']);
    this.validStop = utils.toDate(this.filter.value['customStop']);
    this.appliedInterval = 'CUSTOM';
    this.loadData();
  }

  /**
   * Loads the first page of data within validStart and validStop
   */
  loadData() {
    const options: GetAlarmsOptions = {
      detail: true,
    };
    if (this.validStart) {
      options.start = this.validStart.toISOString();
    }
    if (this.validStop) {
      options.stop = this.validStop.toISOString();
    }

    this.dataSource.loadAlarms(options);
  }

  /**
   * Loads the next page of data starting at where the previous page was cut off.
   * This not 100% waterproof as data may have arrived with generation time between
   * the last visible data and the offscreen record. This is unlikely to cause
   * practical problems.
   */
  loadMoreData() {
    const options: GetAlarmsOptions = {
      detail: true,
    };
    if (this.validStart) {
      options.start = this.validStart.toISOString();
    }
    this.dataSource.loadMoreData(options);
  }

  ngOnDestroy() {
    this.dataSource?.disconnect();
  }
}
