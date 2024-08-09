import { SelectionModel } from '@angular/cdk/collections';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { UntypedFormControl, UntypedFormGroup } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { Activity, GetActivitiesOptions, MessageService, Synchronizer, WebappSdkModule, YaColumnInfo, YaSelectOption, YamcsService, utils } from '@yamcs/webapp-sdk';
import { BehaviorSubject, debounceTime } from 'rxjs';
import { AuthService } from '../../core/services/AuthService';
import { InstancePageTemplateComponent } from '../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../shared/instance-toolbar/instance-toolbar.component';
import { SetFailedDialogComponent } from '../set-failed-dialog/set-failed-dialog.component';
import { ActivityDurationComponent } from '../shared/activity-duration.component';
import { ActivityIconComponent } from '../shared/activity-icon.component';
import { ActivitiesDataSource } from './activities.datasource';

const defaultInterval = 'NO_LIMIT';

@Component({
  standalone: true,
  templateUrl: './activity-list.component.html',
  styleUrl: './activity-list.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ActivityDurationComponent,
    ActivityIconComponent,
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    WebappSdkModule,
  ],
})
export class ActivityListComponent {

  validStart: Date | null;
  validStop: Date | null;

  // Same as filter.interval but only updates after 'apply' in case of custom dates
  // This allows showing visual indicators for the visible data set before a custom
  // range is actually applied.
  appliedInterval: string;

  filterForm = new UntypedFormGroup({
    status: new UntypedFormControl([]),
    filter: new UntypedFormControl(),
    type: new UntypedFormControl([]),
    interval: new UntypedFormControl(defaultInterval),
    customStart: new UntypedFormControl(null),
    customStop: new UntypedFormControl(null),
  });

  dataSource: ActivitiesDataSource;

  columns: YaColumnInfo[] = [
    { id: 'select', label: '', alwaysVisible: true },
    { id: 'status', label: 'Status', alwaysVisible: true },
    { id: 'id', label: 'Id', alwaysVisible: true },
    { id: 'start', label: 'Start', visible: true },
    { id: 'type', label: 'Type', visible: true },
    { id: 'detail', label: 'Detail', visible: true },
    { id: 'duration', label: 'Duration', visible: true },
    { id: 'actions', label: '', alwaysVisible: true },
  ];

  typeOptions$ = new BehaviorSubject<YaSelectOption[]>([
    { id: 'MANUAL', label: 'Manual', icon: 'emoji_people' },
  ]);

  statusOptions$ = new BehaviorSubject<YaSelectOption[]>([
    { id: 'RUNNING', label: 'Running', icon: 'cached' },
    { id: 'SUCCESSFUL', label: 'Successful', icon: 'check_circle' },
    { id: 'CANCELLED', label: 'Cancelled', icon: 'stop_circle' },
    { id: 'FAILED', label: 'Failed', icon: 'highlight_off' },
  ]);

  intervalOptions: YaSelectOption[] = [
    { id: 'PT1H', label: 'Last hour' },
    { id: 'PT6H', label: 'Last 6 hours' },
    { id: 'P1D', label: 'Last 24 hours' },
    { id: 'NO_LIMIT', label: 'No limit' },
    { id: 'CUSTOM', label: 'Custom', group: true },
  ];

  private status: string[] = [];
  private type: string[] = [];
  private filter: string;

  selection = new SelectionModel<Activity>(true, []);

  tableTrackerFn = (index: number, item: Activity) => item.id;

  constructor(
    readonly yamcs: YamcsService,
    private authService: AuthService,
    private router: Router,
    private route: ActivatedRoute,
    title: Title,
    synchronizer: Synchronizer,
    private messageService: MessageService,
    private dialog: MatDialog,
  ) {
    title.setTitle('Activities');

    yamcs.yamcsClient.getExecutors(yamcs.instance!).then(executors => {
      for (const executor of executors) {
        this.typeOptions$.next([
          ...this.typeOptions$.value,
          {
            id: executor.type,
            label: executor.displayName,
            icon: executor.icon || 'new_label',
          },
        ]);
      }
    }).catch(err => this.messageService.showError(err));

    this.dataSource = new ActivitiesDataSource(yamcs, synchronizer);
    this.dataSource.startStreaming();

    this.initializeOptions();
    this.loadData();

    this.filterForm.get('filter')!.valueChanges.pipe(
      debounceTime(400),
    ).forEach(filter => {
      this.filter = filter;
      this.loadData();
    });

    this.filterForm.get('status')!.valueChanges.forEach(status => {
      this.status = status;
      this.loadData();
    });

    this.filterForm.get('type')!.valueChanges.forEach(type => {
      this.type = type;
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
    if (queryParams.has('filter')) {
      this.filter = queryParams.get('filter') || '';
      this.filterForm.get('filter')!.setValue(this.filter);
    }
    if (queryParams.has('status')) {
      this.type = queryParams.getAll('status')!;
      this.filterForm.get('status')!.setValue(this.status);
    }
    if (queryParams.has('type')) {
      this.type = queryParams.getAll('type')!;
      this.filterForm.get('type')!.setValue(this.type);
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
        this.validStop = this.yamcs.getMissionTime();
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
    const options: GetActivitiesOptions = {};
    if (this.validStart) {
      options.start = this.validStart.toISOString();
    }
    if (this.validStop) {
      options.stop = this.validStop.toISOString();
    }
    if (this.filter) {
      options.q = this.filter;
    }
    if (this.status.length) {
      options.status = this.status;
    }
    if (this.type.length) {
      options.type = this.type;
    }

    this.dataSource.loadActivities(options)
      .catch(err => this.messageService.showError(err));
  }

  loadMoreData() {
    const options: GetActivitiesOptions = {};
    if (this.validStart) {
      options.start = this.validStart.toISOString();
    }
    if (this.status) {
      options.status = this.status;
    }
    if (this.filter) {
      options.q = this.filter;
    }

    this.dataSource.loadMoreData(options);
  }

  private updateURL() {
    this.router.navigate([], {
      replaceUrl: true,
      relativeTo: this.route,
      queryParams: {
        filter: this.filter || null,
        status: this.type.length ? this.status : null,
        type: this.type.length ? this.type : null,
        interval: this.appliedInterval,
        customStart: this.appliedInterval === 'CUSTOM' ? this.filterForm.value['customStart'] : null,
        customStop: this.appliedInterval === 'CUSTOM' ? this.filterForm.value['customStop'] : null,
      },
      queryParamsHandling: 'merge',
    });
  }

  mayControlActivities() {
    return this.authService.getUser()!.hasSystemPrivilege('ControlActivities');
  }

  isAllSelected() {
    return false;
    /*
    const numSelected = this.selection.selected.length;
    const numRows = this.dataSource.filteredData.length;
    return numSelected === numRows && numRows > 0;*/
  }

  masterToggle() {
    /*
    this.isAllSelected() ?
      this.selection.clear() :
      this.dataSource.filteredData.forEach(row => this.selection.select(row));*/
  }

  toggleOne(row: Activity) {
    if (!this.selection.isSelected(row) || this.selection.selected.length > 1) {
      this.selection.clear();
    }
    this.selection.toggle(row);
  }

  isGroupCancelEnabled() {
    // Allow if at least one of the selected activities is cancellable
    for (const activity of this.selection.selected) {
      if (!activity.stop) {
        return true;
      }
    }
    return false;
  }

  cancelSelectedActivities() {
    for (const activity of this.selection.selected) {
      if (!activity.stop) { }
      this.cancelActivity(activity);
    }
  }

  cancelActivity(activity: Activity) {
    this.yamcs.yamcsClient.cancelActivity(this.yamcs.instance!, activity.id)
      .catch(err => this.messageService.showError(err));
  }

  setSuccessful(activity: Activity) {
    this.yamcs.yamcsClient.completeManualActivity(this.yamcs.instance!, activity.id)
      .catch(err => this.messageService.showError(err));
  }

  setFailed(activity: Activity) {
    this.dialog.open(SetFailedDialogComponent, {
      width: '400px',
      data: { activity },
    }).afterClosed().subscribe(result => {
      if (result) {
        this.yamcs.yamcsClient.completeManualActivity(this.yamcs.instance!, activity.id, {
          failureReason: result.failureReason,
        }).catch(err => this.messageService.showError(err));
      }
    });
  }
}
