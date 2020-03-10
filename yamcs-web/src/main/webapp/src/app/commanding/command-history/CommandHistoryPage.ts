import { ChangeDetectionStrategy, Component, ComponentFactoryResolver, ViewChild } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { rowAnimation } from '../../animations';
import { GetCommandHistoryOptions } from '../../client';
import { AuthService } from '../../core/services/AuthService';
import { ConfigService, WebsiteConfig } from '../../core/services/ConfigService';
import { PrintService } from '../../core/services/PrintService';
import { Synchronizer } from '../../core/services/Synchronizer';
import { YamcsService } from '../../core/services/YamcsService';
import { Option, Select } from '../../shared/forms/Select';
import { ColumnInfo } from '../../shared/template/ColumnChooser';
import { User } from '../../shared/User';
import * as utils from '../../shared/utils';
import { subtractDuration } from '../../shared/utils';
import { CommandHistoryDataSource } from './CommandHistoryDataSource';
import { CommandHistoryPrintable } from './CommandHistoryPrintable';
import { CommandHistoryRecord } from './CommandHistoryRecord';


const defaultInterval = 'PT1H';

@Component({
  templateUrl: './CommandHistoryPage.html',
  styleUrls: ['./CommandHistoryPage.css'],
  animations: [rowAnimation],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandHistoryPage {

  instance: string;

  selectedRecord$ = new BehaviorSubject<CommandHistoryRecord | null>(null);

  @ViewChild('intervalSelect')
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
    customStart: new FormControl(null),
    customStop: new FormControl(null),
  });

  dataSource: CommandHistoryDataSource;

  columns: ColumnInfo[] = [
    { id: 'commandId', label: 'ID' },
    { id: 'generationTimeUTC', label: 'Time', alwaysVisible: true },
    { id: 'comment', label: 'Comment', visible: true },
    { id: 'command', label: 'Command', alwaysVisible: true },
    { id: 'issuer', label: 'Issuer' },
    { id: 'queued', label: 'Queued', visible: true },
    { id: 'released', label: 'Released', visible: true },
    { id: 'sent', label: 'Sent', visible: true },
    { id: 'acknowledgments', label: 'Extra acknowledgments', visible: true },
    { id: 'completion', label: 'Completion', visible: true },
  ];

  intervalOptions: Option[] = [
    { id: 'PT1H', label: 'Last hour' },
    { id: 'PT6H', label: 'Last 6 hours' },
    { id: 'P1D', label: 'Last 24 hours' },
    { id: 'NO_LIMIT', label: 'No limit' },
    { id: 'CUSTOM', label: 'Custom', group: true },
  ];

  // Would prefer to use formGroup, but when using valueChanges this
  // only is updated after the callback...
  private filter: string;

  user: User;
  config: WebsiteConfig;

  constructor(
    private yamcs: YamcsService,
    configService: ConfigService,
    authService: AuthService,
    private router: Router,
    private route: ActivatedRoute,
    private componentFactoryResolver: ComponentFactoryResolver,
    private printService: PrintService,
    title: Title,
    synchronizer: Synchronizer,
  ) {
    this.config = configService.getConfig();
    this.user = authService.getUser()!;
    title.setTitle('Command History');
    this.instance = yamcs.getInstance();

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
        this.filterForm.get('customStart')!.setValue(utils.printLocalDate(customStart, 'hhmm'));
        this.filterForm.get('customStop')!.setValue(utils.printLocalDate(customStop, 'hhmm'));
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
      this.filterForm.get('interval')!.setValue(defaultInterval);
    } else if (interval === 'CUSTOM') {
      // For simplicity reasons, just reset to default 1h interval.
      this.filterForm.get('interval')!.setValue(defaultInterval);
    } else {
      this.validStop = this.yamcs.getMissionTime();
      this.validStart = subtractDuration(this.validStop, interval);
      this.loadData();
    }
  }

  // Used in table trackBy to prevent continuous row recreation
  // tableTrackerFn = (index: number, entry: CommandHistoryEntry) => ;

  applyCustomDates() {
    this.validStart = utils.toDate(this.filterForm.value['customStart']);
    this.validStop = utils.toDate(this.filterForm.value['customStop']);
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

  showResend() {
    return this.config.features.tc && this.user.hasAnyObjectPrivilegeOfType('Command');
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

  selectRecord(rec: CommandHistoryRecord) {
    this.selectedRecord$.next(rec);
  }

  printReport() {
    const data = this.dataSource.records$.value.slice().reverse();
    const factory = this.componentFactoryResolver.resolveComponentFactory(CommandHistoryPrintable);
    this.printService.printComponent(factory, 'Command Report', data);
  }
}
