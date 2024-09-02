import { ChangeDetectionStrategy, Component, OnInit, input } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { AuditRecord, GetAuditRecordsOptions, MessageService, WebappSdkModule, YaSelectOption, YamcsService, utils } from '@yamcs/webapp-sdk';
import { InstancePageTemplateComponent } from '../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../shared/instance-toolbar/instance-toolbar.component';
import { AlarmsPageTabsComponent } from '../alarms-page-tabs/alarms-page-tabs.component';

@Component({
  standalone: true,
  selector: 'app-alarms-action-log-tab',
  templateUrl: './action-log-tab.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AlarmsPageTabsComponent,
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    WebappSdkModule,
  ],
})
export class ActionLogTabComponent implements OnInit {

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
    interval: new FormControl<string | null>('NO_LIMIT'),
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

  dataSource = new MatTableDataSource<AuditRecord>();

  constructor(
    title: Title,
    readonly yamcs: YamcsService,
    private messageService: MessageService,
    private router: Router,
    private route: ActivatedRoute,
  ) {
    title.setTitle('Alarms');
  }

  ngOnInit(): void {
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
      } else if (nextInterval) {
        this.validStop = new Date();
        this.validStart = utils.subtractDuration(this.validStop, nextInterval);
        this.appliedInterval = nextInterval;
        this.loadData();
      }
    });
  }

  private initializeOptions() {
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
      this.appliedInterval = 'NO_LIMIT';
      this.validStop = null;
      this.validStart = null;
    }
  }

  jumpToNow() {
    this.filterForm.get('interval')!.setValue('NO_LIMIT');
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
      service: 'AlarmsApi',
    };
    if (this.validStart) {
      options.start = this.validStart.toISOString();
    }
    if (this.validStop) {
      options.stop = this.validStop.toISOString();
    }

    this.yamcs.yamcsClient.getAuditRecords(this.yamcs.instance!, options)
      .then(page => this.dataSource.data = page.records || [])
      .catch(err => this.messageService.showError(err));
  }

  private updateURL() {
    const { controls } = this.filterForm;
    this.router.navigate([], {
      replaceUrl: true,
      relativeTo: this.route,
      queryParams: {
        interval: this.appliedInterval,
        customStart: this.appliedInterval === 'CUSTOM' ? controls['customStart'].value : null,
        customStop: this.appliedInterval === 'CUSTOM' ? controls['customStop'].value : null,
      },
      queryParamsHandling: 'merge',
    });
  }
}
