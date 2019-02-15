import { SelectionModel } from '@angular/cdk/collections';
import { ChangeDetectionStrategy, Component, OnDestroy, OnInit } from '@angular/core';
import { MatDialog } from '@angular/material';
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
export class AlarmsPage implements OnInit, OnDestroy {

  instance: Instance;

  selectedAlarm$ = new BehaviorSubject<Alarm | null>(null);

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

  alarmDetailSubscription: Subscription;

  constructor(private yamcs: YamcsService, title: Title, private dialog: MatDialog) {
    title.setTitle('Alarms - Yamcs');
    this.instance = this.yamcs.getInstance();
  }

  // Used in table trackBy to prevent continuous row recreation
  tableTrackerFn = (index: number, alarm: Alarm) => alarm.triggerValue.id.name;

  ngOnInit() {
    this.dataSource = new AlarmsDataSource(this.yamcs);
    this.dataSource.loadAlarms('realtime');

    this.alarmDetailSubscription = this.dataSource.alarms$.subscribe(alarms => {
      const selectedAlarm = this.selectedAlarm$.value;
      if (selectedAlarm) {
        for (const alarm of alarms) {
          if (this.isSameAlarm(alarm, selectedAlarm)) {
            this.selectedAlarm$.next(alarm);
          }
        }
      }
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
    if (true) {
      return;
    }
    if (!this.selection.isSelected(row) || this.selection.selected.length > 1) {
      this.selection.clear();
    }
    this.selection.toggle(row);
  }

  acknowledgeSelectedAlarms() {
    const dialogRef = this.dialog.open(AcknowledgeAlarmDialog, {
      width: '400px',
    });
    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        // this.loadLayouts();
      }
    });
  }

  selectAlarm(alarm: Alarm) {
    this.selectedAlarm$.next(alarm);
  }

  ngOnDestroy() {
    if (this.alarmDetailSubscription) {
      this.alarmDetailSubscription.unsubscribe();
    }
  }
}
