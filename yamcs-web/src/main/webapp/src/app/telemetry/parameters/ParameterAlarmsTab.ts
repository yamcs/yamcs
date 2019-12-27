import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { GetAlarmsOptions } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';
import { Option } from '../../shared/forms/Select';
import * as utils from '../../shared/utils';
import { ParameterAlarmsDataSource } from './ParameterAlarmsDataSource';

const defaultInterval = 'P1M';

@Component({
  templateUrl: './ParameterAlarmsTab.html',
  styleUrls: ['./ParameterAlarmsTab.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParameterAlarmsTab {

  qualifiedName: string;

  intervalOptions: Option[] = [
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

  filter = new FormGroup({
    interval: new FormControl(defaultInterval),
    customStart: new FormControl(null),
    customStop: new FormControl(null),
  });

  dataSource: ParameterAlarmsDataSource;

  constructor(route: ActivatedRoute, private yamcs: YamcsService) {
    this.qualifiedName = route.parent!.snapshot.paramMap.get('qualifiedName')!;
    this.dataSource = new ParameterAlarmsDataSource(yamcs, this.qualifiedName);

    this.validStop = yamcs.getMissionTime();
    this.validStart = utils.subtractDuration(this.validStop, defaultInterval);
    this.appliedInterval = defaultInterval;
    this.loadData();

    this.filter.get('interval')!.valueChanges.forEach(nextInterval => {
      if (nextInterval === 'CUSTOM') {
        const customStart = this.validStart || new Date();
        const customStop = this.validStop || new Date();
        this.filter.get('customStart')!.setValue(utils.printLocalDate(customStart, 'hhmm'));
        this.filter.get('customStop')!.setValue(utils.printLocalDate(customStop, 'hhmm'));
      } else if (nextInterval === 'NO_LIMIT') {
        this.validStart = null;
        this.validStop = null;
        this.appliedInterval = nextInterval;
        this.loadData();
      } else {
        this.validStop = yamcs.getMissionTime();
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
}
