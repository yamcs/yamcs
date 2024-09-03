import { Clipboard } from '@angular/cdk/clipboard';
import { AfterViewInit, ChangeDetectionStrategy, Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { CommandHistoryRecord, ConfigService, GetCommandHistoryOptions, MessageService, PrintService, Synchronizer, User, WebappSdkModule, WebsiteConfig, YaColumnChooser, YaColumnInfo, YamcsService, utils } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { AuthService } from '../../../core/services/AuthService';
import { HexComponent } from '../../../shared/hex/hex.component';
import { InstancePageTemplateComponent } from '../../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../../shared/instance-toolbar/instance-toolbar.component';
import { AcknowledgmentIconComponent } from '../acknowledgment-icon/acknowledgment-icon.component';
import { CommandArgumentsComponent } from '../command-arguments/command-arguments.component';
import { CommandDetailComponent } from '../command-detail/command-detail.component';
import { CommandHistoryPrintableComponent } from '../command-history-printable/command-history-printable.component';
import { ExportCommandsDialogComponent } from '../export-commands-dialog/export-commands-dialog.component';
import { CommandDownloadLinkPipe } from '../shared/command-download-link.pipe';
import { TransmissionConstraintsIconComponent } from '../transmission-constraints-icon/transmission-constraints-icon.component';
import { CommandHistoryDataSource } from './command-history.datasource';


const defaultInterval = 'PT1H';

@Component({
  standalone: true,
  templateUrl: './command-history-list.component.html',
  styleUrl: './command-history-list.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AcknowledgmentIconComponent,
    CommandArgumentsComponent,
    CommandDetailComponent,
    CommandDownloadLinkPipe,
    HexComponent,
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    WebappSdkModule,
    TransmissionConstraintsIconComponent,
  ],
})
export class CommandHistoryListComponent implements OnInit, AfterViewInit, OnDestroy {

  selectedRecord$ = new BehaviorSubject<CommandHistoryRecord | null>(null);

  validStart: Date | null;
  validStop: Date | null;

  // Same as filter.interval but only updates after 'apply' in case of custom dates
  // This allows showing visual indicators for the visible data set before a custom
  // range is actually applied.
  appliedInterval: string;

  filterForm = new FormGroup({
    filter: new FormControl<string | null>(null),
    queue: new FormControl<string | null>(null),
    interval: new FormControl<string | null>(defaultInterval),
    customStart: new FormControl<string | null>(null),
    customStop: new FormControl<string | null>(null),
  });

  dataSource: CommandHistoryDataSource;

  columns: YaColumnInfo[] = [
    { id: 'commandId', label: 'ID', alwaysVisible: true },
    { id: 'generationTime', label: 'Time', alwaysVisible: true },
    { id: 'comment', label: 'Comment', visible: true },
    { id: 'command', label: 'Command', alwaysVisible: true },
    { id: 'issuer', label: 'Issuer' },
    { id: 'queue', label: 'Queue' },
    { id: 'queued', label: 'Queued', visible: true },
    { id: 'released', label: 'Released', visible: true },
    { id: 'sent', label: 'Sent', visible: true },
    { id: 'acknowledgments', label: 'Extra acknowledgments', visible: true },
    { id: 'completion', label: 'Completion', visible: true },
    { id: 'actions', label: '', alwaysVisible: true },
  ];

  // Added dynamically based on actual commands.
  aliasColumns$ = new BehaviorSubject<YaColumnInfo[]>([]);

  @ViewChild(YaColumnChooser)
  columnChooser: YaColumnChooser;

  user: User;
  config: WebsiteConfig;

  private subscriptions: Subscription[] = [];

  constructor(
    readonly yamcs: YamcsService,
    configService: ConfigService,
    authService: AuthService,
    private messageService: MessageService,
    private router: Router,
    private route: ActivatedRoute,
    private printService: PrintService,
    title: Title,
    synchronizer: Synchronizer,
    private clipboard: Clipboard,
    private dialog: MatDialog,
  ) {
    this.config = configService.getConfig();
    this.user = authService.getUser()!;
    title.setTitle('Command history');

    this.dataSource = new CommandHistoryDataSource(this.yamcs, synchronizer);
  }

  ngOnInit(): void {
    this.initializeOptions();
    this.loadData();

    this.filterForm.get('filter')!.valueChanges.pipe(
      debounceTime(400),
    ).forEach(filter => {
      this.loadData();
    });

    this.filterForm.get('queue')!.valueChanges.forEach(queue => {
      this.loadData();
    });

    this.filterForm.get('interval')!.valueChanges.forEach(nextInterval => {
      if (nextInterval === 'CUSTOM') {
        const customStart = this.validStart || this.yamcs.getMissionTime();
        const customStop = this.validStop || this.yamcs.getMissionTime();
        this.filterForm.get('customStart')!.setValue(utils.toISOString(customStart));
        this.filterForm.get('customStop')!.setValue(utils.toISOString(customStop));
      } else if (nextInterval === 'NO_LIMIT') {
        this.validStart = null;
        this.validStop = null;
        this.appliedInterval = nextInterval;
        this.loadData();
      } else if (nextInterval) {
        this.validStop = this.yamcs.getMissionTime();
        this.validStart = utils.subtractDuration(this.validStop, nextInterval);
        this.appliedInterval = nextInterval;
        this.loadData();
      }
    });
  }

  ngAfterViewInit() {
    this.subscriptions.push(this.dataSource.namespaces$.subscribe(namespaces => {
      // Reset alias columns
      for (const aliasColumn of this.aliasColumns$.value) {
        const idx = this.columns.indexOf(aliasColumn);
        if (idx !== -1) {
          this.columns.splice(idx, 1);
        }
      }
      const aliasColumns = [];
      for (const namespace of namespaces) {
        const aliasColumn = { id: namespace, label: namespace, alwaysVisible: true };
        aliasColumns.push(aliasColumn);
      }
      const insertIdx = this.columns.findIndex(column => column.id === 'command');
      this.columns.splice(insertIdx + 1, 0, ...aliasColumns); // Insert after name column
      this.aliasColumns$.next(aliasColumns);
      this.columnChooser.recalculate(this.columns);
    }));
  }

  private initializeOptions() {
    const queryParams = this.route.snapshot.queryParamMap;
    if (queryParams.has('filter')) {
      const filter = queryParams.get('filter')!;
      this.filterForm.get('filter')!.setValue(filter);
    }
    if (queryParams.has('queue')) {
      const queue = queryParams.get('queue')!;
      this.filterForm.get('queue')!.setValue(queue);
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
        this.validStart = utils.subtractDuration(this.validStop, this.appliedInterval);
      }
    } else {
      this.appliedInterval = defaultInterval;
      this.validStop = this.yamcs.getMissionTime();
      this.validStart = utils.subtractDuration(this.validStop, defaultInterval);
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
      this.validStop = this.yamcs.getMissionTime();
      this.validStart = utils.subtractDuration(this.validStop, interval);
      this.loadData();
    }
  }

  startStreaming() {
    this.filterForm.get('interval')!.setValue('NO_LIMIT');
    this.dataSource.startStreaming();
  }

  stopStreaming() {
    this.dataSource.stopStreaming();
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
    const { controls } = this.filterForm;
    this.updateURL();
    const options: GetCommandHistoryOptions = {};
    if (this.validStart) {
      options.start = this.validStart.toISOString();
    }
    if (this.validStop) {
      options.stop = this.validStop.toISOString();
    }
    const filter = controls['filter'].value;
    if (filter) {
      options.q = filter;
    }
    const queue = controls['queue'].value;
    if (queue) {
      options.queue = queue;
    }
    this.dataSource.loadEntries(options)
      .catch(err => this.messageService.showError(err));
  }

  loadMoreData() {
    const { controls } = this.filterForm;
    const options: GetCommandHistoryOptions = {};
    if (this.validStart) {
      options.start = this.validStart.toISOString();
    }
    const filter = controls['filter'].value;
    if (filter) {
      options.q = filter;
    }
    const queue = controls['queue'].value;
    if (queue) {
      options.queue = queue;
    }

    this.dataSource.loadMoreData(options)
      .catch(err => this.messageService.showError(err));
  }

  showResend() {
    return this.config.tc && this.user.hasAnyObjectPrivilegeOfType('Command');
  }

  showCommandExports() {
    return this.config.commandExports;
  }

  private updateURL() {
    const { controls } = this.filterForm;
    this.router.navigate([], {
      replaceUrl: true,
      relativeTo: this.route,
      queryParams: {
        filter: controls['filter'].value || null,
        queue: controls['queue'].value || null,
        interval: this.appliedInterval,
        customStart: this.appliedInterval === 'CUSTOM' ? controls['customStart'].value : null,
        customStop: this.appliedInterval === 'CUSTOM' ? controls['customStop'].value : null,
      },
      queryParamsHandling: 'merge',
    });
  }

  copyHex(command: CommandHistoryRecord) {
    const hex = utils.convertBase64ToHex(command.binary);
    this.clipboard.copy(hex);
  }

  copyBinary(command: CommandHistoryRecord) {
    const raw = window.atob(command.binary);
    this.clipboard.copy(raw);
  }

  selectRecord(rec: CommandHistoryRecord) {
    this.selectedRecord$.next(rec);
  }

  printReport() {
    const data = this.dataSource.records$.value.slice().reverse();
    this.printService.printComponent(CommandHistoryPrintableComponent, 'Command Report', data);
  }

  exportCsv() {
    this.dialog.open(ExportCommandsDialogComponent, {
      width: '400px',
      data: {
        start: this.validStart,
        stop: this.validStop,
        q: this.filterForm.controls['filter'].value,
      },
    });
  }

  ngOnDestroy() {
    this.subscriptions.forEach(sub => sub.unsubscribe());
  }
}
