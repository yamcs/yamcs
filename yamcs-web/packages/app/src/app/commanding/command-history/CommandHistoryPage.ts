import { ChangeDetectionStrategy, Component, ViewChild } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { GetCommandHistoryOptions, Instance } from '@yamcs/client';
import { BehaviorSubject } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { rowAnimation } from '../../animations';
import { PreferenceStore } from '../../core/services/PreferenceStore';
import { Synchronizer } from '../../core/services/Synchronizer';
import { YamcsService } from '../../core/services/YamcsService';
import { Option, Select } from '../../shared/forms/Select';
import { ColumnInfo } from '../../shared/template/ColumnChooser';
import { subtractDuration } from '../../shared/utils';
import { CommandHistoryDataSource } from './CommandHistoryDataSource';
import { CommandHistoryRecord } from './CommandHistoryRecord';


const defaultInterval = 'PT1H';
const deprecatedCols = ['stages', 'transmissionConstraints', 'release', 'sequenceNumber', 'verifications'];

@Component({
  templateUrl: './CommandHistoryPage.html',
  styleUrls: ['./CommandHistoryPage.css'],
  animations: [rowAnimation],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandHistoryPage {

  instance: Instance;

  selectedRecord$ = new BehaviorSubject<CommandHistoryRecord | null>(null);

  @ViewChild('intervalSelect', { static: false })
  intervalSelect: Select;

  validStart: Date | null;
  validStop: Date | null;

  // Same as filter.interval but only updates after 'apply' in case of custom dates
  // This allows showing visual indicators for the visible data set before a custom
  // range is actually applied.
  appliedInterval: string;

  filterForm = new FormGroup({
    filter: new FormControl(),
    interval: new FormControl(defaultInterval),
    customStart: new FormControl(null, [
      Validators.pattern(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/),
    ]),
    customStop: new FormControl(null, [
      Validators.pattern(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/),
    ]),
  });

  dataSource: CommandHistoryDataSource;

  columns: ColumnInfo[] = [
    { id: 'commandId', label: 'ID' },
    { id: 'generationTimeUTC', label: 'Time', alwaysVisible: true },
    { id: 'comment', label: 'Comment' },
    { id: 'command', label: 'Command', alwaysVisible: true },
    { id: 'issuer', label: 'Issuer' },
    { id: 'queued', label: 'Queued' },
    { id: 'released', label: 'Released' },
    { id: 'sent', label: 'Sent' },
    { id: 'acknowledgments', label: 'Extra acknowledgments' },
    { id: 'completion', label: 'Completion' },
  ];

  displayedColumns = [
    // 'significance', // not stored in cmdhist?
    'generationTimeUTC',
    'comment',
    'command',
    'queued',
    'released',
    'sent',
    'acknowledgments',
    'completion',
  ];

  intervalOptions: Option[] = [
    { id: 'PT1H', label: 'Last hour', selected: true },
    { id: 'PT6H', label: 'Last 6 hours' },
    { id: 'P1D', label: 'Last 24 hours' },
    { id: 'NO_LIMIT', label: 'No limit' },
    { id: 'CUSTOM', label: 'Custom', group: true },
  ];

  // Would prefer to use formGroup, but when using valueChanges this
  // only is updated after the callback...
  private filter: string;

  constructor(
    private yamcs: YamcsService,
    private preferenceStore: PreferenceStore,
    private router: Router,
    private route: ActivatedRoute,
    title: Title,
    synchronizer: Synchronizer,
  ) {
    title.setTitle('Command History');
    this.instance = yamcs.getInstance();

    const cols = preferenceStore.getVisibleColumns('cmdhist', deprecatedCols);
    if (cols && cols.length) {
      this.displayedColumns = cols;
    }

    this.dataSource = new CommandHistoryDataSource(this.yamcs, synchronizer);

    this.initializeOptions();
    this.loadData();

    this.filterForm.get('filter')!.valueChanges.pipe(
      debounceTime(400),
    ).forEach(filter => {
      this.filter = filter;
      this.loadData();
    });

    this.filterForm.get('interval')!.valueChanges.forEach(nextInterval => {
      if (nextInterval === 'CUSTOM') {
        const customStart = this.validStart || new Date();
        const customStop = this.validStop || new Date();
        this.filterForm.get('customStart')!.setValue(customStart.toISOString());
        this.filterForm.get('customStop')!.setValue(customStop.toISOString());
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

  private initializeOptions() {
    const queryParams = this.route.snapshot.queryParamMap;
    if (queryParams.has('filter')) {
      this.filter = queryParams.get('filter') || '';
      this.filterForm.get('filter')!.setValue(this.filter);
    }
    if (queryParams.has('interval')) {
      this.appliedInterval = queryParams.get('interval')!;
      for (const option of this.intervalOptions) {
        option.selected = (option.id === this.appliedInterval);
      }
      this.filterForm.get('interval')!.setValue(this.appliedInterval);
      if (this.appliedInterval === 'CUSTOM') {
        const customStart = queryParams.get('customStart')!;
        this.filterForm.get('customStart')!.setValue(customStart);
        this.validStart = new Date(customStart);
        const customStop = queryParams.get('customStop')!;
        this.filterForm.get('customStop')!.setValue(customStop);
        this.validStop = new Date(customStop);
      } else if (this.appliedInterval === 'NO_LIMIT') {
        this.validStart = null;
        this.validStop = null;
      } else {
        this.validStop = this.yamcs.getMissionTime();
        this.validStart = subtractDuration(this.validStop, this.appliedInterval);
      }
    } else {
      this.appliedInterval = defaultInterval;
      this.validStop = this.yamcs.getMissionTime();
      this.validStart = subtractDuration(this.validStop, defaultInterval);
    }
  }

  jumpToNow() {
    const interval = this.filterForm.value['interval'];
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

  // Used in table trackBy to prevent continuous row recreation
  // tableTrackerFn = (index: number, entry: CommandHistoryEntry) => ;

  applyCustomDates() {
    this.validStart = new Date(this.filterForm.value['customStart']);
    this.validStop = new Date(this.filterForm.value['customStop']);
    this.appliedInterval = 'CUSTOM';
    this.loadData();
  }

  loadData() {
    this.updateURL();
    const options: GetCommandHistoryOptions = {};
    if (this.validStart) {
      options.start = this.validStart.toISOString();
    }
    if (this.validStop) {
      options.stop = this.validStop.toISOString();
    }
    if (this.filter) {
      options.q = this.filter;
    }
    this.dataSource.loadEntries('realtime', options);
  }

  loadMoreData() {
    const options: GetCommandHistoryOptions = {};
    if (this.validStart) {
      options.start = this.validStart.toISOString();
    }
    if (this.filter) {
      options.q = this.filter;
    }

    this.dataSource.loadMoreData({});
  }

  private updateURL() {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        filter: this.filter || null,
        interval: this.appliedInterval,
        customStart: this.appliedInterval === 'CUSTOM' ? this.filterForm.value['customStart'] : null,
        customStop: this.appliedInterval === 'CUSTOM' ? this.filterForm.value['customStop'] : null,
      },
      queryParamsHandling: 'merge',
    });
  }

  updateColumns(displayedColumns: string[]) {
    this.displayedColumns = displayedColumns;
    this.preferenceStore.setVisibleColumns('cmdhist', displayedColumns);
  }

  updateInterval(interval: string) {
    this.filterForm.get('interval')!.setValue(interval);
  }

  selectRecord(rec: CommandHistoryRecord) {
    this.selectedRecord$.next(rec);
  }
}
