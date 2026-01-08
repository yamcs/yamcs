import { SelectionModel } from '@angular/cdk/collections';
import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { Alarm, BaseComponent, WebappSdkModule } from '@yamcs/webapp-sdk';
import { AgoComponent } from '../../shared/ago/ago.component';
import { AlarmStateIconComponent } from '../alarm-state-icon/alarm-state-icon.component';
import { AlarmsDataSource } from '../alarms.datasource';

@Component({
  selector: 'app-alarms-table',
  templateUrl: './alarm-table.component.html',
  styleUrl: './alarm-table.component.css',
  imports: [AgoComponent, AlarmStateIconComponent, WebappSdkModule],
})
export class AlarmsTableComponent extends BaseComponent implements OnInit {
  displayedColumns = [
    'state',
    'severity',
    'time',
    'alarm',
    'type',
    'trip_value',
    'live_value',
    'actions',
  ];

  @Input()
  dataSource: AlarmsDataSource;

  @Input()
  selection: SelectionModel<Alarm>;

  @Input()
  view:
    | 'standard'
    | 'unacknowledged'
    | 'acknowledged'
    | 'shelved'
    | 'all'
    | 'pending' = 'standard';

  @Input()
  mayControl = false;

  @Output()
  acknowledgeAlarm = new EventEmitter<Alarm>();

  @Output()
  shelveAlarm = new EventEmitter<Alarm>();

  @Output()
  unshelveAlarm = new EventEmitter<Alarm>();

  // Used in table trackBy to prevent continuous row recreation
  tableTrackerFn = (index: number, alarm: Alarm) => {
    return `${alarm.triggerTime}__${alarm.id.namespace}__${alarm.id.name}__${alarm.seqNum}`;
  };

  ngOnInit(): void {
    if (this.view === 'pending') {
      this.displayedColumns.splice(
        this.displayedColumns.length - 1,
        0,
        'violations',
      );
    }
  }

  toggleOne(row: Alarm) {
    if (!this.selection.isSelected(row) || this.selection.selected.length > 1) {
      this.selection.clear();
    }
    this.selection.toggle(row);
    this.openDetailPane();
  }

  hideAlarm(alarm: Alarm) {
    if (this.view === 'pending') {
      return !alarm.pending;
    } else if (this.view === 'standard') {
      return alarm.pending || !!alarm.shelveInfo;
    } else if (this.view === 'all') {
      return alarm.pending;
    } else if (this.view === 'unacknowledged') {
      return alarm.pending || !!alarm.shelveInfo || alarm.acknowledged;
    } else if (this.view === 'acknowledged') {
      return alarm.pending || !!alarm.shelveInfo || !alarm.acknowledged;
    } else if (this.view === 'shelved') {
      return alarm.pending || !alarm.shelveInfo;
    }
  }
}
