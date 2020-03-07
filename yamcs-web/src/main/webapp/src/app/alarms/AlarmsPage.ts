import { SelectionModel } from '@angular/cdk/collections';
import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject, Subscription } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { Alarm, EditAlarmOptions, Instance } from '../client';
import { AuthService } from '../core/services/AuthService';
import { YamcsService } from '../core/services/YamcsService';
import { Option } from '../shared/forms/Select';
import { AcknowledgeAlarmDialog } from './AcknowledgeAlarmDialog';
import { AlarmsDataSource } from './AlarmsDataSource';
import { ShelveAlarmDialog } from './ShelveAlarmDialog';

@Component({
  templateUrl: './AlarmsPage.html',
  styleUrls: ['./AlarmsPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlarmsPage implements OnDestroy {

  filterForm = new FormGroup({
    filter: new FormControl(),
    view: new FormControl('standard'),
  });

  instance: Instance;

  // Alarm to show in detail pane (only on single selection)
  detailAlarm$ = new BehaviorSubject<Alarm | null>(null);

  dataSource: AlarmsDataSource;
  selection = new SelectionModel<Alarm>(false, []);

  viewOptions: Option[] = [
    { id: 'standard', label: 'Standard view (ack & unack)' },
    { id: 'unacknowledged', label: 'Unacknowledged alarms' },
    { id: 'acknowledged', label: 'Acknowledged alarms' },
    { id: 'shelved', label: 'Shelved alarms' },
    { id: 'all', label: 'All alarms' },
  ];

  private selectionSubscription: Subscription;
  private alarmsSubscription: Subscription;

  // Would prefer to use formGroup, but when using valueChanges this
  // only is updated after the callback...
  view$ = new BehaviorSubject('standard');
  private filter: string;

  constructor(
    private yamcs: YamcsService,
    private route: ActivatedRoute,
    private router: Router,
    title: Title,
    private dialog: MatDialog,
    private authService: AuthService,
  ) {
    title.setTitle('Alarms');
    this.instance = this.yamcs.getInstance();
    this.selectionSubscription = this.selection.changed.subscribe(() => {
      const selected = this.selection.selected;
      if (selected.length === 1) {
        this.detailAlarm$.next(selected[0]);
      } else {
        this.detailAlarm$.next(null);
      }
    });

    this.dataSource = new AlarmsDataSource(this.yamcs);
    this.dataSource.loadAlarms('realtime');

    this.alarmsSubscription = this.dataSource.alarms$.subscribe(alarms => {
      // Update detail pane
      const detailAlarm = this.detailAlarm$.value;
      if (detailAlarm) {
        for (const alarm of alarms) {
          if (this.isSameAlarm(alarm, detailAlarm)) {
            this.detailAlarm$.next(alarm);
            break;
          }
        }
      }

      // Adjust selection model (object identities may have changed)
      const oldAlarms = this.selection.selected;
      this.selection.clear();
      for (const oldAlarm of oldAlarms) {
        for (const newAlarm of alarms) {
          if (this.isSameAlarm(oldAlarm, newAlarm)) {
            this.selection.toggle(newAlarm);
            break;
          }
        }
      }
    });

    this.initializeOptions();

    this.filterForm.get('filter')!.valueChanges.pipe(
      debounceTime(400),
    ).forEach(filter => {
      this.filter = filter;
      this.updateURL();
      this.dataSource.setFilter(filter);
    });

    this.filterForm.get('view')!.valueChanges.forEach(view => {
      this.view$.next(view);
      this.updateURL();
      this.selection.clear();
    });
  }

  private initializeOptions() {
    const queryParams = this.route.snapshot.queryParamMap;
    if (queryParams.has('filter')) {
      this.filter = queryParams.get('filter') || '';
      this.filterForm.get('filter')!.setValue(this.filter);
    }
    if (queryParams.has('view')) {
      const view = queryParams.get('view')!;
      this.view$.next(view);
      this.filterForm.get('view')!.setValue(view);
    }
  }

  private isSameAlarm(alarm1: Alarm, alarm2: Alarm) {
    return alarm1.seqNum === alarm2.seqNum
      && alarm1.id.namespace === alarm2.id.namespace
      && alarm1.id.name === alarm2.id.name
      && alarm1.triggerTime === alarm2.triggerTime;
  }

  acknowledgeAlarms(alarms: Alarm[]) {
    this.dialog.open(AcknowledgeAlarmDialog, {
      width: '400px',
      data: { alarms },
    });
  }

  shelveAlarms(alarms: Alarm[]) {
    this.dialog.open(ShelveAlarmDialog, {
      width: '400px',
      data: { alarms },
    });
  }

  unshelveAlarms(alarms: Alarm[]) {
    for (const alarm of alarms) {
      const processor = this.yamcs.getProcessor();
      const options: EditAlarmOptions = {
        state: 'unshelved',
      };
      const alarmId = alarm.id.namespace + '/' + alarm.id.name;
      this.yamcs.yamcsClient.editAlarm(processor.instance, processor.name, alarmId, alarm.seqNum, options);
    }
  }

  clearAlarms(alarms: Alarm[]) {
    for (const alarm of alarms) {
      const processor = this.yamcs.getProcessor();
      const options: EditAlarmOptions = {
        state: 'cleared',
      };
      const alarmId = alarm.id.namespace + '/' + alarm.id.name;
      this.yamcs.yamcsClient.editAlarm(processor.instance, processor.name, alarmId, alarm.seqNum, options);
    }
  }

  mayControlAlarms() {
    return this.authService.getUser()!.hasSystemPrivilege('ControlAlarms');
  }

  private updateURL() {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        filter: this.filter || null,
        view: this.view$.value || null,
      },
      queryParamsHandling: 'merge',
    });
  }

  ngOnDestroy() {
    if (this.selectionSubscription) {
      this.selectionSubscription.unsubscribe();
    }
    if (this.alarmsSubscription) {
      this.alarmsSubscription.unsubscribe();
    }
  }
}
