import { SelectionModel } from '@angular/cdk/collections';
import { ChangeDetectionStrategy, Component, ViewChild } from '@angular/core';
import { UntypedFormControl, UntypedFormGroup, Validators } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { Gap, GetGapsOptions, MessageService, SelectComponent, SelectOption, YamcsService, utils } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { RequestMultipleRangesPlaybackDialog } from './RequestMultipleRangesPlaybackDialog';
import { RequestSingleRangePlaybackDialog } from './RequestSingleRangePlaybackDialog';

@Component({
  templateUrl: './GapsPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GapsPage {

  @ViewChild('intervalSelect')
  intervalSelect: SelectComponent;

  validStart: Date | null;
  validStop: Date | null;

  // Same as filter.interval but only updates after 'apply' in case of custom dates
  // This allows showing visual indicators for the visible data set before a custom
  // range is actually applied.
  appliedInterval: string;

  filterForm = new UntypedFormGroup({
    apid: new UntypedFormControl([]),
    interval: new UntypedFormControl('PT1H', [Validators.required]),
    customStart: new UntypedFormControl(null),
    customStop: new UntypedFormControl(null),
  });

  displayedColumns = [
    'select',
    'start',
    'stop',
    'apid',
    'duration',
    'startSequence',
    'stopSequence',
    'packetCount',
    'actions',
  ];

  apidOptions$ = new BehaviorSubject<SelectOption[]>([]);

  intervalOptions: SelectOption[] = [
    { id: 'PT1H', label: 'Last hour' },
    { id: 'PT3H', label: 'Last 3 hours' },
    { id: 'PT6H', label: 'Last 6 hours' },
    { id: 'PT12H', label: 'Last 12 hours' },
    { id: 'P1D', label: 'Last 24 hours' },
    { id: 'P2D', label: 'Last 48 hours' },
    { id: 'CUSTOM', label: 'Custom', group: true },
  ];

  // Would prefer to use formGroup, but when using valueChanges this
  // only is updated after the callback...
  private apid: string[] = [];

  dataSource = new MatTableDataSource<Gap>();
  selection = new SelectionModel<Gap>(true, []);
  hasMore$ = new BehaviorSubject<boolean>(false);

  constructor(
    private yamcs: YamcsService,
    title: Title,
    private dialog: MatDialog,
    private messageService: MessageService,
    private router: Router,
    private route: ActivatedRoute,
  ) {
    title.setTitle('Gaps');

    yamcs.yamcsClient.getApids(yamcs.instance!).then(apids => {
      for (const apid of apids) {
        this.apidOptions$.next([
          ...this.apidOptions$.value,
          {
            id: String(apid),
            label: `APID ${apid}`,
          }
        ]);
      }
    });

    this.initializeOptions();
    this.loadData();

    this.filterForm.get('apid')!.valueChanges.forEach(apid => {
      this.apid = apid;
      this.loadData();
    });

    this.filterForm.get('interval')!.valueChanges.forEach(nextInterval => {
      const now = this.yamcs.getMissionTime();
      if (nextInterval === 'CUSTOM') {
        const customStart = this.validStart || now;
        const customStop = this.validStop || now;
        this.filterForm.get('customStart')!.setValue(utils.toISOString(customStart));
        this.filterForm.get('customStop')!.setValue(utils.toISOString(customStop));
      } else {
        this.validStop = null;
        this.validStart = utils.subtractDuration(now, nextInterval);
        this.appliedInterval = nextInterval;
        this.loadData();
      }
    });
  }

  private initializeOptions() {
    const queryParams = this.route.snapshot.queryParamMap;
    if (queryParams.has('apid')) {
      this.apid = queryParams.getAll('apid')!;
      this.filterForm.get('apid')!.setValue(this.apid);
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
      } else {
        this.validStop = this.yamcs.getMissionTime();
        this.validStart = utils.subtractDuration(this.validStop, this.appliedInterval);
      }
    } else {
      this.appliedInterval = 'PT1H';
      this.validStop = this.yamcs.getMissionTime();
      this.validStart = utils.subtractDuration(this.validStop, this.appliedInterval);
    }
  }

  applyCustomDates() {
    this.validStart = utils.toDate(this.filterForm.value['customStart']);
    this.validStop = utils.toDate(this.filterForm.value['customStop']);
    this.appliedInterval = 'CUSTOM';
    this.loadData();
  }

  loadData() {
    this.selection.clear();
    this.updateURL();

    const options: GetGapsOptions = {
      limit: 500
    };
    if (this.validStart) {
      options.start = this.validStart.toISOString();
    }
    if (this.validStop) {
      options.stop = this.validStop.toISOString();
    }
    if (this.apid.length) {
      options.apids = this.apid.map(apid => Number(apid));
    }

    this.yamcs.yamcsClient.getGaps(this.yamcs.instance!, options)
      .then(page => {
        const gaps = (page.gaps || [])
          .sort(utils.objectCompareFn('-start', '-apid'));

        // No reverse pagination on this resource, so we use the token
        // to detect whether a smaller request should be page.
        // (if we didn't only the head of the entire data range would be visible)
        this.hasMore$.next(!!page.continuationToken);
        this.dataSource.data = gaps;
      })
      .catch(err => this.messageService.showError(err));
  }

  isAllSelected() {
    const numSelected = this.selection.selected.length;
    const numRows = this.dataSource.filteredData.filter(row => row.start && row.stop).length;
    return numSelected === numRows && numRows > 0;
  }

  private updateURL() {
    this.router.navigate([], {
      replaceUrl: true,
      relativeTo: this.route,
      queryParams: {
        apid: this.apid.length ? this.apid : null,
        interval: this.appliedInterval,
        customStart: this.appliedInterval === 'CUSTOM' ? this.filterForm.value['customStart'] : null,
        customStop: this.appliedInterval === 'CUSTOM' ? this.filterForm.value['customStop'] : null,
      },
      queryParamsHandling: 'merge',
    });
  }

  masterToggle() {
    this.isAllSelected() ?
      this.selection.clear() :
      this.dataSource.filteredData.forEach(row => {
        if (row.start && row.stop) {
          this.selection.select(row);
        }
      });
  }

  toggleOne(row: Gap) {
    if (!row.start || !row.stop) {
      return;
    }
    if (!this.selection.isSelected(row) || this.selection.selected.length > 1) {
      this.selection.clear();
    }
    this.selection.toggle(row);
  }

  openPlaybackDialog(row?: Gap) {
    const gaps = row ? [row] : this.selection.selected;
    let dialogRef;
    if (gaps.length <= 1) {
      dialogRef = this.dialog.open(RequestSingleRangePlaybackDialog, {
        data: { gap: gaps[0] },
        width: '500px',
      });
    } else {
      dialogRef = this.dialog.open(RequestMultipleRangesPlaybackDialog, {
        data: { gaps },
        width: '500px',
      });
    }

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.yamcs.yamcsClient.requestPlayback(this.yamcs.instance!, result.link, {
          ranges: result.ranges,
        }).then(() => this.messageService.showInfo('Playback requested'))
          .catch(err => this.messageService.showError(err));
      }
    });
  }
}
