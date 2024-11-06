import { ChangeDetectionStrategy, Component } from '@angular/core';
import { UntypedFormControl, UntypedFormGroup } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { MatTableDataSource } from '@angular/material/table';
import { ActivatedRoute, Router } from '@angular/router';
import { ExportParameterValuesOptions, ParameterList, WebappSdkModule, YaSelectOption, YamcsService, utils } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { ExportArchiveDataDialogComponent } from '../../displays/export-archive-data-dialog/export-archive-data-dialog.component';

export interface ValueExport {
  headers: string[];
  records: ValueSnapshot[];
}

export interface ValueSnapshot {
  generationTime: string;
  values: (string | null)[];
}

const defaultInterval = 'PT1H';

@Component({
  standalone: true,
  templateUrl: './parameter-list-historical-data-tab.component.html',
  styleUrl: './parameter-list-historical-data-tab.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class ParameterListHistoricalDataTabComponent {

  plistId: string;
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

  displayedColumns = [
    'generationTime',
    'actions',
  ];
  displayedColumns$ = new BehaviorSubject<string[]>(this.displayedColumns);

  plist$ = new BehaviorSubject<ParameterList | null>(null);
  exportData$ = new BehaviorSubject<ValueExport | null>(null);

  dataSource = new MatTableDataSource<ValueSnapshot>();

  constructor(
    readonly router: Router,
    readonly route: ActivatedRoute,
    readonly yamcs: YamcsService,
    private dialog: MatDialog,
  ) {
    this.plistId = route.snapshot.paramMap.get('list')!;

    this.validStop = yamcs.getMissionTime();
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
        this.validStop = yamcs.getMissionTime();
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

  private loadData() {
    this.updateURL();
    const options: ExportParameterValuesOptions = {
      delimiter: 'TAB',
      limit: 100,
      order: 'desc',
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

    this.yamcs.yamcsClient.getParameterList(this.yamcs.instance!, this.plistId).then(plist => {
      this.plist$.next(plist);

      if (plist.match) {
        this.yamcs.yamcsClient.exportParameterValues(this.yamcs.instance!, {
          ...options,
          list: plist.id,
        }).then(pdata => {
          const exportData = this.processCsv(pdata);
          this.exportData$.next(exportData);
          this.displayedColumns$.next([
            'generationTime',
            ...exportData.headers,
            'actions',
          ]);
          this.dataSource.data = exportData.records;
        });
      }
    });
  }

  private processCsv(csv: string) {
    const records: ValueSnapshot[] = [];
    const lines = csv.split(/\r?\n/);
    for (let i = 1; i < lines.length; i++) {
      const line = lines[i];
      if (!line) {
        continue;
      }
      const cells = line.split(/\t/);
      const snapshot: ValueSnapshot = {
        generationTime: cells[0],
        values: [],
      };
      for (let j = 1; j < cells.length; j++) {
        snapshot.values[j - 1] = cells[j] || null;
      }
      records.push(snapshot);
    }

    const qualifiedNames = lines[0].split(/\t/).slice(1);
    const headers: string[] = qualifiedNames.map(x => utils.getFilename(x)!);
    const result: ValueExport = { headers, records };
    return result;
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
    const plist = this.plist$.value;
    const parameters = plist?.match;
    if (parameters) {
      let filename = plist.name;
      if (this.validStart && this.validStop) {
        filename += '_' + this.validStart.toISOString() + '_' + this.validStop.toISOString();
      } else if (this.validStart) {
        filename += '_' + this.validStart.toISOString();
      } else if (this.validStop) {
        filename += '_' + this.validStop.toISOString();
      }
      filename += '.csv';
      this.dialog.open(ExportArchiveDataDialogComponent, {
        width: '400px',
        data: {
          list: plist.id,
          start: this.validStart,
          stop: this.validStop,
          filename,
        }
      });
    }
  }
}
