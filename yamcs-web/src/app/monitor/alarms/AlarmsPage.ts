import { Component, ChangeDetectionStrategy, AfterViewInit, ViewChild } from '@angular/core';

import { Alarm, Instance } from '../../../yamcs-client';

import { YamcsService } from '../../core/services/YamcsService';
import { MatTableDataSource, MatSort } from '@angular/material';
import { Observable } from 'rxjs/Observable';
import { Store } from '@ngrx/store';
import { State } from '../../app.reducers';
import { selectCurrentInstance } from '../../core/store/instance.selectors';
import { BehaviorSubject } from 'rxjs/BehaviorSubject';

@Component({
  templateUrl: './AlarmsPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlarmsPage implements AfterViewInit {

  @ViewChild(MatSort)
  sort: MatSort;

  instance$: Observable<Instance>;
  loading$ = new BehaviorSubject<boolean>(false);

  displayedColumns = [
    'severity',
    'parameter',
    'time',
    'value',
    'type',
    'currentValue',
    'violations'
  ];

  dataSource = new MatTableDataSource<Alarm>();

  private alarmsByName: { [key: string]: Alarm } = {};

  constructor(private yamcs: YamcsService, store: Store<State>) {
    this.instance$ = store.select(selectCurrentInstance);
    this.loading$.next(true);
    this.yamcs.getSelectedInstance().getActiveAlarms('realtime').subscribe(alarms => {
      for (const alarm of alarms) {
        this.processAlarm(alarm);
      }
      this.dataSource.data = Object.values(this.alarmsByName);
      this.loading$.next(false);
    });

    this.yamcs.getSelectedInstance().getAlarmUpdates().subscribe(alarm => {
      this.processAlarm(alarm);
      this.dataSource.data = Object.values(this.alarmsByName);
    });
  }

  // Used in table trackBy to prevent continuous row recreation
  tableTrackerFn = (index: number, alarm: Alarm) => alarm.triggerValue.id.name;

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
  }

  processAlarm(alarm: Alarm) {
    switch (alarm.type) {
      case 'ACTIVE':
      case 'TRIGGERED':
      case 'SEVERITY_INCREASED':
      case 'PVAL_UPDATED':
      case 'ACKNOWLEDGED':
        this.alarmsByName[alarm.triggerValue.id.name] = alarm;
        break;
      case 'CLEARED':
        delete this.alarmsByName[alarm.triggerValue.id.name];
        break;
      default:
        console.warn('Unexpected alarm event of type', alarm.type);
    }
  }
}
