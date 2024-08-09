import { ChangeDetectionStrategy, Component, OnInit, input } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { AuditRecord, GetAuditRecordsOptions, MessageService, WebappSdkModule, YaSelectOption, YamcsService, utils } from '@yamcs/webapp-sdk';
import { BehaviorSubject, debounceTime } from 'rxjs';
import { AdminPageTemplateComponent } from '../shared/admin-page-template/admin-page-template.component';
import { AdminToolbarComponent } from '../shared/admin-toolbar/admin-toolbar.component';
import { RequestOption, Row, RowGroup } from './model';

const defaultInterval = 'NO_LIMIT';

@Component({
  standalone: true,
  templateUrl: './admin-action-log.component.html',
  styleUrl: './admin-action-log.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AdminPageTemplateComponent,
    AdminToolbarComponent,
    WebappSdkModule,
  ],
})
export class AdminActionLogComponent implements OnInit {

  filter = input<string>();
  interval = input<string>();
  customStart = input<string>();
  customStop = input<string>();

  validStart: Date | null;
  validStop: Date | null;

  // Same as filter.interval but only updates after 'apply' in case of custom dates
  // This allows showing visual indicators for the visible data set before a custom
  // range is actually applied.
  appliedInterval: string;

  filterForm = new FormGroup({
    filter: new FormControl<string | null>(null),
    interval: new FormControl<string | null>(defaultInterval),
    customStart: new FormControl<string | null>(null),
    customStop: new FormControl<string | null>(null),
  });

  displayedColumns = [
    'time',
    'user',
    'summary',
    'actions',
  ];

  intervalOptions: YaSelectOption[] = [
    { id: 'PT1H', label: 'Last hour' },
    { id: 'PT6H', label: 'Last 6 hours' },
    { id: 'P1D', label: 'Last 24 hours' },
    { id: 'NO_LIMIT', label: 'No limit' },
    { id: 'CUSTOM', label: 'Custom', group: true },
  ];

  rowGroups$ = new BehaviorSubject<RowGroup[]>([]);

  constructor(
    private yamcs: YamcsService,
    private router: Router,
    private route: ActivatedRoute,
    private messageService: MessageService,
    title: Title,
  ) {
    title.setTitle('Admin Area');
  }

  ngOnInit(): void {
    this.initializeOptions();
    this.loadData();

    this.filterForm.get('filter')!.valueChanges.pipe(
      debounceTime(400),
    ).forEach(filter => {
      this.loadData();
    });

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
      } else if (nextInterval) {
        this.validStop = new Date();
        this.validStart = utils.subtractDuration(this.validStop, nextInterval);
        this.appliedInterval = nextInterval;
        this.loadData();
      }
    });
  }

  private initializeOptions() {
    if (this.filter()) {
      const filter = this.filter()!;
      this.filterForm.get('filter')!.setValue(filter);
    }
    if (this.interval()) {
      this.appliedInterval = this.interval()!;
      this.filterForm.get('interval')!.setValue(this.appliedInterval);
      if (this.appliedInterval === 'CUSTOM') {
        const customStart = this.customStart()!;
        this.filterForm.get('customStart')!.setValue(customStart);
        this.validStart = utils.toDate(customStart);
        const customStop = this.customStop()!;
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
      this.appliedInterval = defaultInterval;
      this.validStop = null;
      this.validStart = null;
    }
  }

  jumpToNow() {
    const interval = this.filterForm.value['interval'];
    if (interval === 'NO_LIMIT') {
      // NO_LIMIT may include future data under erratic conditions. Reverting
      // to the default interval is more in line with the wording 'jump to now'.
      this.filterForm.get('interval')!.setValue(defaultInterval);
    } else if (interval === 'CUSTOM') {
      // For simplicity reasons, just reset to default 1h interval.
      this.filterForm.get('interval')!.setValue(defaultInterval);
    } else if (interval) {
      this.validStop = new Date();
      this.validStart = utils.subtractDuration(this.validStop, interval);
      this.loadData();
    }
  }

  applyCustomDates() {
    const { controls } = this.filterForm;
    this.validStart = utils.toDate(controls['customStart'].value);
    this.validStop = utils.toDate(controls['customStop'].value);
    this.appliedInterval = 'CUSTOM';
    this.loadData();
  }

  /**
   * Loads the first page of data within validStart and validStop
   */
  loadData() {
    this.updateURL();
    const options: GetAuditRecordsOptions = {
    };
    if (this.validStart) {
      options.start = this.validStart.toISOString();
    }
    if (this.validStop) {
      options.stop = this.validStop.toISOString();
    }
    if (this.filterForm.controls['filter'].value) {
      options.q = this.filterForm.controls['filter'].value;
    }

    const today = new Date().toISOString().substring(0, 10);
    const yesterday = utils.subtractDuration(new Date(), 'P1D').toISOString().substring(0, 10);
    this.yamcs.yamcsClient.getAuditRecords('_global', options).then(page => {
      const rowGroups = this.groupByDay(page.records || []).map(group => {
        const dataSource = new MatTableDataSource<Row>();
        dataSource.data = group.map(item => {
          const requestOptions: any[] = [{ key: 'Request', value: '' }];
          if (item.request) {
            this.flatten(item.request, requestOptions, '    ');
          }
          return { item, expanded: false, requestOptions };
        });
        let grouper = group[0].time.substring(0, 10);
        if (grouper === today) {
          grouper = 'Today';
        } else if (grouper === yesterday) {
          grouper = 'Yesterday';
        }
        return { grouper, dataSource };
      });
      this.rowGroups$.next(rowGroups);
    }).catch(err => this.messageService.showError(err));
  }

  loadMoreData() {
    /*const options: GetAuditRecordsOptions = {
    };
    if (this.validStart) {
      options.start = this.validStart.toISOString();
    }
    if (this.filter) {
      options.q = this.filter;
    }

    this.dataSource.loadMoreData(options);*/
  }

  private updateURL() {
    const { controls } = this.filterForm;
    this.router.navigate([], {
      replaceUrl: true,
      relativeTo: this.route,
      queryParams: {
        filter: controls['filter'].value || null,
        interval: this.appliedInterval,
        customStart: this.appliedInterval === 'CUSTOM' ? controls['customStart'].value : null,
        customStop: this.appliedInterval === 'CUSTOM' ? controls['customStop'].value : null,
      },
      queryParamsHandling: 'merge',
    });
  }

  private groupByDay(records: AuditRecord[]) {
    const byDay: Array<AuditRecord[]> = [];
    let currentDay: string | undefined;
    let dayRecords: AuditRecord[] = [];
    for (const record of records) {
      const day = record.time.substring(0, 10);
      if (day !== currentDay) {
        currentDay = day;
        if (dayRecords.length) {
          byDay.push(dayRecords);
          dayRecords = [];
        }
      }
      dayRecords.push(record);
    }
    if (dayRecords.length) {
      byDay.push(dayRecords);
    }
    return byDay;
  }

  private flatten(node: { [key: string]: any; }, result: RequestOption[], indent = '') {
    for (const key in node) {
      const value = node[key];
      if (Array.isArray(value)) {
        result.push({ key: indent + key, value: '' });
        for (let i = 0; i < value.length; i++) {
          result.push({ key: indent + '    ' + key + ' ' + (i + 1), value: value[i] });
        }
      } else if (typeof value === 'object') {
        result.push({ key: indent + key, value: '' });
        this.flatten(value, result, indent + '    ');
      } else {
        result.push({ key: indent + key, value: '' + value });
      }
    }
  }
}
