import { Component, ChangeDetectionStrategy } from '@angular/core';

import { YamcsService } from '../../core/services/YamcsService';
import { EventsDataSource } from './EventsDataSource';
import { Title } from '@angular/platform-browser';
import { GetEventsOptions, DownloadEventsOptions } from '@yamcs/client';
import { FormGroup, FormControl, Validators } from '@angular/forms';
import { BehaviorSubject } from 'rxjs/BehaviorSubject';
import { ColumnInfo } from '../../shared/template/ColumnChooser';
import { subtractDuration } from '../../shared/utils';
import { rowAnimation } from '../animations';
import { PreferenceStore } from '../../core/services/PreferenceStore';
import { debounceTime } from 'rxjs/operators';
import { Option } from '../../shared/template/Select';

const defaultInterval = 'PT1H';

@Component({
  templateUrl: './EventsPage.html',
  styleUrls: ['./EventsPage.css'],
  animations: [rowAnimation],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EventsPage {

  validStart: Date | null;
  validStop: Date | null;

  // Same as filter.interval but only updates after 'apply' in case of custom dates
  // This allows showing visual indicators for the visible data set before a custom
  // range is actually applied.
  appliedInterval: string;

  filter = new FormGroup({
    textSearch: new FormControl(),
    severity: new FormControl('INFO'),
    interval: new FormControl(defaultInterval),
    customStart: new FormControl(null, [
      Validators.pattern(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/),
    ]),
    customStop: new FormControl(null, [
      Validators.pattern(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/),
    ]),
  });

  dataSource: EventsDataSource;

  columns: ColumnInfo[] = [
    { id: 'severity', label: 'Severity' },
    { id: 'gentime', label: 'Generation Time', alwaysVisible: true },
    { id: 'message', label: 'Message', alwaysVisible: true },
    { id: 'type', label: 'Type' },
    { id: 'source', label: 'Source' },
    { id: 'rectime', label: 'Reception Time' },
    { id: 'seqNumber', label: 'Sequence Number' },
  ];

  displayedColumns = [
    'severity',
    'gentime',
    'message',
    'type',
    'source',
  ];

  severityOptions: Option[] = [
    { id: 'INFO', label: 'Info level', selected: true },
    { id: 'WATCH', label: 'Watch level' },
    { id: 'WARNING', label: 'Warning level' },
    { id: 'DISTRESS', label: 'Distress level' },
    { id: 'CRITICAL', label: 'Critical level' },
    { id: 'SEVERE', label: 'Severe level' },
  ];

  intervalOptions: Option[] = [
    { id: 'PT1H', label: 'Last hour', selected: true },
    { id: 'PT6H', label: 'Last 6 hours' },
    { id: 'P1D', label: 'Last 24 hours' },
    { id: 'NO_LIMIT', label: 'No limit' },
    { id: 'CUSTOM', label: 'Custom', group: true },
  ];

  downloadURL$ = new BehaviorSubject<string | null>(null);

  // Would prefer to use formGroup, but when using valueChanges this
  // only is updated after the callback...
  private severity = 'INFO';
  private textSearch: string;

  constructor(
    private yamcs: YamcsService,
    private preferenceStore: PreferenceStore,
    title: Title,
  ) {
    title.setTitle('Events - Yamcs');

    const cols = preferenceStore.getVisibleColumns('events');
    if (cols.length) {
      this.displayedColumns = cols;
    }

    this.dataSource = new EventsDataSource(yamcs);

    this.validStop = yamcs.getMissionTime();
    this.validStart = subtractDuration(this.validStop, defaultInterval);
    this.appliedInterval = defaultInterval;
    this.loadData();

    this.filter.get('textSearch')!.valueChanges.pipe(
      debounceTime(400),
    ).forEach(textSearch => {
      this.textSearch = textSearch;
      this.loadData();
    });

    this.filter.get('severity')!.valueChanges.forEach(severity => {
      this.severity = severity;
      this.loadData();
    });

    this.filter.get('interval')!.valueChanges.forEach(nextInterval => {
      if (nextInterval === 'CUSTOM') {
        const customStart = this.validStart || new Date();
        const customStop = this.validStop || new Date();
        this.filter.get('customStart')!.setValue(customStart.toISOString());
        this.filter.get('customStop')!.setValue(customStop.toISOString());
      } else if (nextInterval === 'NO_LIMIT') {
        this.validStart = null;
        this.validStop = null;
        this.appliedInterval = nextInterval;
        this.loadData();
      } else {
        this.validStop = yamcs.getMissionTime();
        this.validStart = subtractDuration(this.validStop, nextInterval);
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
      this.validStart = subtractDuration(this.validStop, interval);
      this.loadData();
    }
  }

  startStreaming() {
    this.filter.get('interval')!.setValue('NO_LIMIT');
    this.dataSource.startStreaming();
  }

  stopStreaming() {
    this.dataSource.stopStreaming();
  }

  applyCustomDates() {
    this.validStart = new Date(this.filter.value['customStart']);
    this.validStop = new Date(this.filter.value['customStop']);
    this.appliedInterval = 'CUSTOM';
    this.loadData();
  }

  /**
   * Loads the first page of data within validStart and validStop
   */
  loadData() {
    const options: GetEventsOptions = {
      severity: this.severity as any,
    };
    if (this.validStart) {
      options.start = this.validStart.toISOString();
    }
    if (this.validStop) {
      options.stop = this.validStop.toISOString();
    }
    if (this.textSearch) {
      options.filter = this.textSearch;
    }

    const dlOptions: DownloadEventsOptions = {
      format: 'csv',
      severity: this.severity as any,
    };
    if (this.validStart) {
      dlOptions.start = this.validStart.toISOString();
    }
    if (this.validStop) {
      dlOptions.stop = this.validStop.toISOString();
    }
    if (this.textSearch) {
      dlOptions.filter = this.textSearch;
    }

    const instanceClient = this.yamcs.getInstanceClient();
    this.dataSource.loadEvents(options).then(events => {
      const downloadURL = instanceClient.getEventsDownloadURL(dlOptions);
      this.downloadURL$.next(downloadURL);
    });
  }

  loadMoreData() {
    const options: GetEventsOptions = {};
    if (this.validStart) {
      options.start = this.validStart.toISOString();
    }

    this.dataSource.loadMoreData(options);
  }

  updateColumns(displayedColumns: string[]) {
    this.displayedColumns = displayedColumns;
    this.preferenceStore.setVisibleColumns('events', displayedColumns);
  }

  updateSeverity(severity: string) {
    this.filter.get('severity')!.setValue(severity);
  }

  updateInterval(interval: string) {
    this.filter.get('interval')!.setValue(interval);
  }
}
