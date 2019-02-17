import { SelectionModel } from '@angular/cdk/collections';
import { AfterViewInit, ChangeDetectionStrategy, Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { MatDialog, MatSort } from '@angular/material';
import { Title } from '@angular/platform-browser';
import { Alarm, Instance } from '@yamcs/client';
import { BehaviorSubject, Subscription } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';
import { AcknowledgeAlarmDialog } from './AcknowledgeAlarmDialog';
import { AlarmsDataSource } from './AlarmsDataSource';

@Component({
  templateUrl: './AlarmsPage.html',
  styleUrls: ['./AlarmsPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlarmsPage implements OnInit, AfterViewInit, OnDestroy {

  @ViewChild(MatSort)
  sort: MatSort;

  instance: Instance;

  // Alarm to show in detail pane (only on single selection)
  detailAlarm$ = new BehaviorSubject<Alarm | null>(null);

  displayedColumns = [
    'select',
    'severity',
    'parameter',
    'time',
    'value',
    'type',
    'currentValue',
    'violations'
  ];

  dataSource: AlarmsDataSource;
  selection = new SelectionModel<Alarm>(true, []);

  private selectionSubscription: Subscription;
  private alarmsSubscription: Subscription;

  constructor(private yamcs: YamcsService, title: Title, private dialog: MatDialog) {
    title.setTitle('Alarms - Yamcs');
    this.instance = this.yamcs.getInstance();
    this.selectionSubscription = this.selection.changed.subscribe(() => {
      const selected = this.selection.selected;
      if (selected.length === 1) {
        this.detailAlarm$.next(selected[0]);
      } else {
        this.detailAlarm$.next(null);
      }
    });
  }

  // Used in table trackBy to prevent continuous row recreation
  tableTrackerFn = (index: number, alarm: Alarm) => alarm.triggerValue.id.name;

  ngOnInit() {
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
  }

  ngAfterViewInit() {
    this.sort.sortChange.subscribe(() => {
      this.dataSource.setSort(this.sort.active, this.sort.direction);
    });
  }

  private isSameAlarm(alarm1: Alarm, alarm2: Alarm) {
    return alarm1.seqNum === alarm2.seqNum
      && alarm1.parameter.qualifiedName === alarm2.parameter.qualifiedName
      && alarm1.triggerValue.generationTimeUTC === alarm2.triggerValue.generationTimeUTC;
  }

  isAllSelected() {
    const numSelected = this.selection.selected.length;
    const numRows = this.dataSource.alarms$.getValue().length;
    return numSelected === numRows;
  }

  masterToggle() {
    this.isAllSelected() ?
        this.selection.clear() :
        this.dataSource.alarms$.getValue().forEach(row => this.selection.select(row));
  }

  toggleOne(row: Alarm) {
    if (!this.selection.isSelected(row) || this.selection.selected.length > 1) {
      this.selection.clear();
    }
    this.selection.toggle(row);
  }

  acknowledgeSelectedAlarms() {
    this.dialog.open(AcknowledgeAlarmDialog, {
      width: '400px',
      data: {
        alarms: this.selection.selected,
      },
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
