import { SelectionModel } from '@angular/cdk/collections';
import { ChangeDetectionStrategy, Component, ViewChild } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { Gap, GetGapsOptions } from '../client';
import { MessageService } from '../core/services/MessageService';
import { YamcsService } from '../core/services/YamcsService';
import { Option, Select } from '../shared/forms/Select';
import * as utils from '../shared/utils';
import { subtractDuration } from '../shared/utils';
import { RequestMultipleRangesPlaybackDialog } from './RequestMultipleRangesPlaybackDialog';
import { RequestSingleRangePlaybackDialog } from './RequestSingleRangePlaybackDialog';

@Component({
  templateUrl: './GapsPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GapsPage {

  @ViewChild('intervalSelect')
  intervalSelect: Select;

  validStart: Date | null;
  validStop: Date | null;

  // Same as filter.interval but only updates after 'apply' in case of custom dates
  // This allows showing visual indicators for the visible data set before a custom
  // range is actually applied.
  appliedInterval: string;

  filterForm = new FormGroup({
    apid: new FormControl('ANY'),
    interval: new FormControl('NO_LIMIT'),
    customStart: new FormControl(null),
    customStop: new FormControl(null),
  });

  displayedColumns = [
    'select',
    'apid',
    'start',
    'stop',
    'duration',
    'startSequence',
    'stopSequence',
    'packetCount',
    'actions',
  ];

  apidOptions$ = new BehaviorSubject<Option[]>([
    { id: 'ANY', label: 'Any APID' },
  ]);

  intervalOptions: Option[] = [
    { id: 'PT1H', label: 'Last hour' },
    { id: 'PT6H', label: 'Last 6 hours' },
    { id: 'P1D', label: 'Last 24 hours' },
    { id: 'NO_LIMIT', label: 'No limit' },
    { id: 'CUSTOM', label: 'Custom', group: true },
  ];

  // Would prefer to use formGroup, but when using valueChanges this
  // only is updated after the callback...
  private apid: string;

  dataSource = new MatTableDataSource<Gap>();
  selection = new SelectionModel<Gap>(true, []);

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
      this.apid = (apid !== 'ANY') ? apid : null;
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
      } else {
        this.validStop = new Date();
        this.validStart = subtractDuration(this.validStop, nextInterval);
        this.appliedInterval = nextInterval;
        this.loadData();
      }
    });
  }

  private initializeOptions() {
    const queryParams = this.route.snapshot.queryParamMap;
    if (queryParams.has('apid')) {
      this.apid = queryParams.get('apid')!;
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
      } else if (this.appliedInterval === 'NO_LIMIT') {
        this.validStart = null;
        this.validStop = null;
      } else {
        this.validStop = new Date();
        this.validStart = subtractDuration(this.validStop, this.appliedInterval);
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

  loadData() {
    this.selection.clear();
    this.updateURL();
    const options: GetGapsOptions = {
    };
    if (this.validStart) {
      options.start = this.validStart.toISOString();
    }
    if (this.validStop) {
      options.stop = this.validStop.toISOString();
    }
    if (this.apid !== null && this.apid !== undefined) {
      options.apid = Number(this.apid);
    }

    this.yamcs.yamcsClient.getGaps(this.yamcs.instance!, options)
      .then(page => this.dataSource.data = page.gaps || [])
      .catch(err => this.messageService.showError(err));
  }

  isAllSelected() {
    const numSelected = this.selection.selected.length;
    const numRows = this.dataSource.data.length;
    return numSelected === numRows;
  }

  private updateURL() {
    this.router.navigate([], {
      replaceUrl: true,
      relativeTo: this.route,
      queryParams: {
        apid: this.apid ?? null,
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
      this.dataSource.data.forEach(row => {
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
