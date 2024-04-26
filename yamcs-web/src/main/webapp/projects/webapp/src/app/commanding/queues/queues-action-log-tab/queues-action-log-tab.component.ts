import { ChangeDetectionStrategy, Component, ViewChild } from '@angular/core';
import { UntypedFormControl, UntypedFormGroup } from '@angular/forms';
import { MatTableDataSource } from '@angular/material/table';
import { ActivatedRoute, Router } from '@angular/router';
import { AuditRecord, GetAuditRecordsOptions, MessageService, WebappSdkModule, YaSelect, YaSelectOption, YamcsService, utils } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-queues-action-log-tab',
  templateUrl: './queues-action-log-tab.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class QueuesActionLogTabComponent {

  @ViewChild('intervalSelect')
  intervalSelect: YaSelect;

  validStart: Date | null;
  validStop: Date | null;

  // Same as filter.interval but only updates after 'apply' in case of custom dates
  // This allows showing visual indicators for the visible data set before a custom
  // range is actually applied.
  appliedInterval: string;

  filterForm = new UntypedFormGroup({
    filter: new UntypedFormControl(),
    interval: new UntypedFormControl('NO_LIMIT'),
    customStart: new UntypedFormControl(null),
    customStop: new UntypedFormControl(null),
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

  // Would prefer to use formGroup, but when using valueChanges this
  // only is updated after the callback...
  private filter: string;

  dataSource = new MatTableDataSource<AuditRecord>();

  constructor(
    readonly yamcs: YamcsService,
    private messageService: MessageService,
    private router: Router,
    private route: ActivatedRoute,
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
    if (queryParams.has('filter')) {
      this.filter = queryParams.get('filter') || '';
      this.filterForm.get('filter')!.setValue(this.filter);
    }
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

  /**
   * Loads the first page of data within validStart and validStop
   */
  loadData() {
    this.updateURL();
    const options: GetAuditRecordsOptions = {
      service: 'QueuesApi',
    };
    if (this.validStart) {
      options.start = this.validStart.toISOString();
    }
    if (this.validStop) {
      options.stop = this.validStop.toISOString();
    }
    if (this.filter) {
      options.q = this.filter;
    }

    this.yamcs.yamcsClient.getAuditRecords(this.yamcs.instance!, options)
      .then(page => this.dataSource.data = page.records || [])
      .catch(err => this.messageService.showError(err));
  }

  private updateURL() {
    this.router.navigate([], {
      replaceUrl: true,
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
}
