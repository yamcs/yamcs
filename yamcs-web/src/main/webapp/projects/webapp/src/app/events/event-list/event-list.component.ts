import { ChangeDetectionStrategy, Component, OnInit, input } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { ConfigService, EventSeverity, ExtraColumnInfo, GetEventsOptions, MessageService, Synchronizer, WebappSdkModule, YaColumnInfo, YaSelectOption, YamcsService, utils } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { AuthService } from '../../core/services/AuthService';
import { InstancePageTemplateComponent } from '../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../shared/instance-toolbar/instance-toolbar.component';
import { CreateEventDialogComponent } from '../create-event-dialog/create-event-dialog.component';
import { EventMessageComponent } from '../event-message/event-message.component';
import { EventSeverityComponent } from '../event-severity/event-severity.component';
import { ExportEventsDialogComponent } from '../export-events-dialog/export-events-dialog.component';
import { EventsDataSource } from './events.datasource';


const defaultInterval = 'PT1H';

@Component({
  standalone: true,
  templateUrl: './event-list.component.html',
  styleUrl: './event-list.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    EventMessageComponent,
    EventSeverityComponent,
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    WebappSdkModule,
  ],
})
export class EventListComponent implements OnInit {

  filter = input<string>();
  severity = input<EventSeverity>();
  source = input<string[]>();
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
    severity: new FormControl<EventSeverity | null>('INFO'),
    source: new FormControl<string[] | null>([]),
    interval: new FormControl<string | null>(defaultInterval),
    customStart: new FormControl<string | null>(null),
    customStop: new FormControl<string | null>(null),
  });

  dataSource: EventsDataSource;

  columns: YaColumnInfo[] = [
    { id: 'severity', label: 'Severity', visible: true },
    { id: 'gentime', label: 'Generation time', alwaysVisible: true },
    { id: 'message', label: 'Message', alwaysVisible: true },
    { id: 'source', label: 'Source', visible: true },
    { id: 'type', label: 'Type', visible: true },
    { id: 'rectime', label: 'Reception time' },
    { id: 'seqNumber', label: 'Sequence number' },
  ];

  /**
   * Columns specific to a Yamcs deployment
   */
  extraColumns: ExtraColumnInfo[] = [];

  sourceOptions$ = new BehaviorSubject<YaSelectOption[]>([]);

  downloadURL$ = new BehaviorSubject<string | null>(null);

  constructor(
    readonly yamcs: YamcsService,
    private authService: AuthService,
    private dialog: MatDialog,
    configService: ConfigService,
    private messageService: MessageService,
    private router: Router,
    private route: ActivatedRoute,
    title: Title,
    synchronizer: Synchronizer,
  ) {
    title.setTitle('Events');

    // Consider site-specific configuration
    const eventConfig = configService.getConfig().events;
    if (eventConfig) {
      this.extraColumns = eventConfig.extraColumns || [];
      for (const extraColumn of this.extraColumns) {
        for (let i = 0; i < this.columns.length; i++) {
          if (this.columns[i].id === extraColumn.after) {
            this.columns.splice(i + 1, 0, extraColumn);
            break;
          }
        }
      }
    }

    yamcs.yamcsClient.getEventSources(yamcs.instance!).then(sources => {
      for (const source of sources) {
        this.sourceOptions$.next([
          ...this.sourceOptions$.value,
          {
            id: source,
            label: source,
          },
        ]);
      }
    });

    this.dataSource = new EventsDataSource(yamcs, synchronizer);

    // Add new sources to source filter
    this.dataSource.sources$.subscribe(sources => {
      this.sourceOptions$.next(sources.map(source => {
        return { id: source, label: source };
      }));
    });
  }

  ngOnInit(): void {
    this.initializeOptions();
    this.loadData();

    this.filterForm.get('filter')!.valueChanges.pipe(
      debounceTime(400),
    ).forEach(filter => {
      this.loadData();
    });

    this.filterForm.get('severity')!.valueChanges.forEach(severity => {
      this.loadData();
    });

    this.filterForm.get('source')!.valueChanges.forEach(source => {
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

  private initializeOptions() {
    if (this.filter()) {
      const filter = this.filter()!;
      this.filterForm.get('filter')!.setValue(filter);
    }
    if (this.severity()) {
      const severity = this.severity()!;
      this.filterForm.get('severity')!.setValue(severity);
    }
    if (this.source()) {
      const source = this.source()!;
      this.filterForm.get('source')!.setValue(source);
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
    const interval = this.filterForm.controls['interval'].value;
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
    const { controls } = this.filterForm;
    this.updateURL();
    const options: GetEventsOptions = {
      severity: controls['severity'].value!,
    };
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
    const source = controls['source'].value;
    if (source?.length) {
      options.source = source;
    }

    this.dataSource.loadEvents(options)
      .catch(err => this.messageService.showError(err));
  }

  loadMoreData() {
    const { controls } = this.filterForm;
    const options: GetEventsOptions = {
      severity: controls['severity'].value!,
    };
    if (this.validStart) {
      options.start = this.validStart.toISOString();
    }
    const filter = controls['filter'].value;
    if (filter) {
      options.q = filter;
    }
    const source = controls['source'].value;
    if (source?.length) {
      options.source = source;
    }

    this.dataSource.loadMoreData(options)
      .catch(err => this.messageService.showError(err));
  }

  private updateURL() {
    const { controls } = this.filterForm;
    this.router.navigate([], {
      replaceUrl: true,
      relativeTo: this.route,
      queryParams: {
        filter: controls['filter'].value || null,
        severity: controls['severity'].value,
        source: controls['source'].value ? controls['source'].value : null,
        interval: this.appliedInterval,
        customStart: this.appliedInterval === 'CUSTOM' ? controls['customStart'].value : null,
        customStop: this.appliedInterval === 'CUSTOM' ? controls['customStop'].value : null,
      },
      queryParamsHandling: 'merge',
    });
  }

  mayWriteEvents() {
    return this.authService.getUser()!.hasSystemPrivilege('WriteEvents');
  }

  createEvent() {
    const dialogInstance = this.dialog.open(CreateEventDialogComponent, {
      width: '400px',
    });
    dialogInstance.afterClosed().subscribe(result => {
      if (result) {
        this.jumpToNow();
      }
    });
  }

  exportEvents() {
    const { controls } = this.filterForm;
    this.dialog.open(ExportEventsDialogComponent, {
      width: '400px',
      data: {
        severity: controls['severity'].value,
        start: this.validStart,
        stop: this.validStop,
        q: controls['filter'].value,
        source: controls['source'].value,
        sourceOptions: this.sourceOptions$.value,
      },
    });
  }
}
