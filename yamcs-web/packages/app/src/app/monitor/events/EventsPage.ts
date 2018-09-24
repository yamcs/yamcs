import { ChangeDetectionStrategy, Component, Inject, ViewChild } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { MatDialog } from '@angular/material';
import { Title } from '@angular/platform-browser';
import { DownloadEventsOptions, GetEventsOptions } from '@yamcs/client';
import { BehaviorSubject } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { AppConfig, APP_CONFIG, ExtraColumnInfo } from '../../core/config/AppConfig';
import { AuthService } from '../../core/services/AuthService';
import { PreferenceStore } from '../../core/services/PreferenceStore';
import { YamcsService } from '../../core/services/YamcsService';
import { ColumnInfo } from '../../shared/template/ColumnChooser';
import { Option, Select } from '../../shared/template/Select';
import { subtractDuration } from '../../shared/utils';
import { rowAnimation } from '../animations';
import { CreateEventDialog } from './CreateEventDialog';
import { EventsDataSource } from './EventsDataSource';


const defaultInterval = 'PT1H';

@Component({
  templateUrl: './EventsPage.html',
  styleUrls: ['./EventsPage.css'],
  animations: [rowAnimation],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EventsPage {

  @ViewChild('intervalSelect')
  intervalSelect: Select;

  validStart: Date | null;
  validStop: Date | null;

  // Same as filter.interval but only updates after 'apply' in case of custom dates
  // This allows showing visual indicators for the visible data set before a custom
  // range is actually applied.
  appliedInterval: string;

  filter = new FormGroup({
    textSearch: new FormControl(),
    severity: new FormControl('INFO'),
    source: new FormControl('ANY'),
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

  /**
   * Columns specific to a site
   */
  extraColumns: ExtraColumnInfo[] = [];

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

  sourceOptions: Option[] = [
    { id: 'ANY', label: 'Any source', selected: true },
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
  private source: string;
  private textSearch: string;

  constructor(
    private yamcs: YamcsService,
    private authService: AuthService,
    private preferenceStore: PreferenceStore,
    private dialog: MatDialog,
    @Inject(APP_CONFIG) appConfig: AppConfig,
    title: Title,
  ) {
    title.setTitle('Events - Yamcs');

    // Consider site-specific configuration
    if (appConfig.events) {
      const eventConfig = appConfig.events;
      this.extraColumns = eventConfig.extraColumns || [];
      for (const extraColumn of this.extraColumns) {
        for (let i = 0; i < this.columns.length; i++) {
          if (this.columns[i].id === extraColumn.after) {
            this.columns.splice(i + 1, 0, extraColumn);
            break;
          }
        }
      }
      if (eventConfig.displayedColumns) {
        this.displayedColumns = eventConfig.displayedColumns;
      }
    }

    const cols = preferenceStore.getVisibleColumns('events').filter(el => {
      // Filter out extraColumns (maybe from another instance - we should maybe store this per instance)
      for (const column of this.columns) {
        if (column.id === el) {
          return true;
        }
      }
    });
    if (cols.length) {
      this.displayedColumns = cols;
    }

    yamcs.getInstanceClient()!.getEventSources().then(sources => {
      for (const source of sources) {
        this.sourceOptions.push({ id: source, label: source });
      }
    });

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

    this.filter.get('source')!.valueChanges.forEach(source => {
      this.source = (source !== 'ANY') ? source : null;
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
      this.intervalSelect.select(defaultInterval);
    } else if (interval === 'CUSTOM') {
      // For simplicity reasons, just reset to default 1h interval.
      this.intervalSelect.select(defaultInterval);
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
      options.q = this.textSearch;
    }
    if (this.source) {
      options.source = this.source;
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
      dlOptions.q = this.textSearch;
    }
    if (this.source) {
      dlOptions.source = this.source;
    }

    const instanceClient = this.yamcs.getInstanceClient()!;
    this.dataSource.loadEvents(options).then(events => {
      const downloadURL = instanceClient.getEventsDownloadURL(dlOptions);
      this.downloadURL$.next(downloadURL);
    });
  }

  loadMoreData() {
    const options: GetEventsOptions = {
      severity: this.severity as any,
    };
    if (this.validStart) {
      options.start = this.validStart.toISOString();
    }
    if (this.textSearch) {
      options.q = this.textSearch;
    }
    if (this.source) {
      options.source = this.source;
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

  updateSource(source: string) {
    this.filter.get('source')!.setValue(source);
  }

  updateInterval(interval: string) {
    this.filter.get('interval')!.setValue(interval);
  }

  mayWriteEvents() {
    return this.authService.getUser()!.hasSystemPrivilege('WriteEvents');
  }

  createEvent() {
    const dialogInstance = this.dialog.open(CreateEventDialog, {
      width: '400px',
    });
    dialogInstance.afterClosed().subscribe(result => {
      if (result) {
        this.jumpToNow();
      }
    });
  }
}
