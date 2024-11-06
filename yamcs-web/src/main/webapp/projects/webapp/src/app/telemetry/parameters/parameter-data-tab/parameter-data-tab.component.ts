import { Clipboard } from '@angular/cdk/clipboard';
import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, input } from '@angular/core';
import { UntypedFormControl, UntypedFormGroup } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, Router } from '@angular/router';
import { ConfigService, GetParameterValuesOptions, MessageService, WebappSdkModule, YaSelectOption, YamcsService, utils } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { AlarmLevelComponent } from '../../../shared/alarm-level/alarm-level.component';
import { HexComponent } from '../../../shared/hex/hex.component';
import { ExportParameterDataDialogComponent } from '../export-parameter-data-dialog/export-parameter-data-dialog.component';
import { ParameterValuesTableComponent } from '../parameter-values-table/parameter-values-table.component';
import { ParameterDataDataSource } from './parameter-data.datasource';

const defaultInterval = 'PT1H';

@Component({
  standalone: true,
  templateUrl: './parameter-data-tab.component.html',
  styleUrl: './parameter-data-tab.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AlarmLevelComponent,
    HexComponent,
    ParameterValuesTableComponent,
    WebappSdkModule,
  ],
})
export class ParameterDataTabComponent implements OnInit, OnDestroy {

  qualifiedName = input.required<string>({ alias: 'parameter' });

  intervalOptions: YaSelectOption[] = [
    { id: 'PT1H', label: 'Last hour' },
    { id: 'PT6H', label: 'Last 6 hours' },
    { id: 'P1D', label: 'Last 24 hours' },
    { id: 'NO_LIMIT', label: 'No limit' },
    { id: 'CUSTOM', label: 'Custom' },
  ];

  validStart: Date | null;
  validStop: Date | null;

  // Same as filter.interval but only updates after 'apply' in case of custom dates
  // This allows showing visual indicators for the visible data set before a custom
  // range is actually applied.
  appliedInterval: string;

  filterForm = new UntypedFormGroup({
    interval: new UntypedFormControl(defaultInterval),
    customStart: new UntypedFormControl(null),
    customStop: new UntypedFormControl(null),
  });

  dataSource: ParameterDataDataSource;
  downloadURL$ = new BehaviorSubject<string | null>(null);

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    readonly yamcs: YamcsService,
    private dialog: MatDialog,
    private clipboard: Clipboard,
    private messageService: MessageService,
    private configService: ConfigService,
  ) { }

  ngOnInit() {
    const qualifiedName = this.qualifiedName();
    this.dataSource = new ParameterDataDataSource(this.yamcs, qualifiedName);

    this.validStop = this.yamcs.getMissionTime();
    this.validStart = utils.subtractDuration(this.validStop, defaultInterval);
    this.appliedInterval = defaultInterval;

    this.initializeOptions();
    this.loadData();

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
      } else {
        this.validStop = this.yamcs.getMissionTime();
        this.validStart = utils.subtractDuration(this.validStop, nextInterval);
        this.appliedInterval = nextInterval;
        this.loadData();
      }
    });
  }

  private initializeOptions() {
    const queryParams = this.route.snapshot.queryParamMap;
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
    } else {
      this.validStop = this.yamcs.getMissionTime();
      this.validStart = utils.subtractDuration(this.validStop, interval);
      this.loadData();
    }
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
    const options: GetParameterValuesOptions = {
      source: this.configService.isParameterArchiveEnabled()
        ? 'ParameterArchive' : 'replay',
    };
    if (this.validStart) {
      // When descending, Yamcs does not include start bound, so make sure
      // the user's indicated start is included.
      const start = new Date(this.validStart.getTime());
      start.setUTCMilliseconds(this.validStart.getUTCMilliseconds() - 1);
      options.start = start.toISOString();
    }
    if (this.validStop) {
      options.stop = this.validStop.toISOString();
    }

    this.dataSource.loadParameterValues(options)
      .catch(err => this.messageService.showError(err));
  }

  /**
   * Loads the next page of data starting at where the previous page was cut off.
   * This not 100% waterproof as data may have arrived with generation time between
   * the last visible data and the offscreen record. This is unlikely to cause
   * practical problems.
   */
  loadMoreData() {
    const options: GetParameterValuesOptions = {
      source: this.configService.isParameterArchiveEnabled()
        ? 'ParameterArchive' : 'replay',
    };
    if (this.validStart) {
      // When descending, Yamcs does not include start bound, so make sure
      // the user's indicated start is included.
      const start = new Date(this.validStart.getTime());
      start.setUTCMilliseconds(this.validStart.getUTCMilliseconds() - 1);
      options.start = start.toISOString();
    }
    this.dataSource.loadMoreData(options)
      .catch(err => this.messageService.showError(err));
  }

  copyHex(base64: string) {
    const hex = utils.convertBase64ToHex(base64);
    this.clipboard.copy(hex);
  }

  copyBinary(base64: string) {
    const raw = window.atob(base64);
    this.clipboard.copy(raw);
  }

  private updateURL() {
    this.router.navigate([], {
      replaceUrl: true,
      relativeTo: this.route,
      queryParams: {
        interval: this.appliedInterval,
        customStart: this.appliedInterval === 'CUSTOM' ? this.filterForm.value['customStart'] : null,
        customStop: this.appliedInterval === 'CUSTOM' ? this.filterForm.value['customStop'] : null,
      },
      queryParamsHandling: 'merge',
    });
  }

  exportParameterData() {
    this.dialog.open(ExportParameterDataDialogComponent, {
      width: '400px',
      data: {
        parameter: this.qualifiedName(),
        start: this.validStart,
        stop: this.validStop,
      }
    });
  }

  ngOnDestroy() {
    this.dataSource?.disconnect();
  }
}
