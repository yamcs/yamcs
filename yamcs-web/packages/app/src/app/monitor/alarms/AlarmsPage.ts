import { SelectionModel } from '@angular/cdk/collections';
import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { MatDialog } from '@angular/material';
import { Title } from '@angular/platform-browser';
import { Alarm, Instance } from '@yamcs/client';
import { BehaviorSubject } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';
import { AcknowledgeAlarmDialog } from './AcknowledgeAlarmDialog';
import { AlarmsDataSource } from './AlarmsDataSource';



@Component({
  templateUrl: './AlarmsPage.html',
  styleUrls: ['./AlarmsPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlarmsPage implements OnInit {

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

  constructor(private yamcs: YamcsService, title: Title, private dialog: MatDialog) {
    title.setTitle('Alarms - Yamcs');
    this.instance = this.yamcs.getInstance();
  }

  // Used in table trackBy to prevent continuous row recreation
  tableTrackerFn = (index: number, alarm: Alarm) => alarm.triggerValue.id.name;

  ngOnInit() {
    this.dataSource = new AlarmsDataSource(this.yamcs);
    this.dataSource.loadAlarms('realtime');
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
}
