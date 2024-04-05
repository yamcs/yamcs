import { SelectionModel } from '@angular/cdk/collections';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Alarm, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { AgoComponent } from '../../shared/ago/ago.component';
import { AlarmLevelComponent } from '../../shared/alarm-level/alarm-level.component';
import { AlarmStateIconComponent } from '../alarm-state-icon/alarm-state-icon.component';
import { AlarmsDataSource } from '../alarms.datasource';

@Component({
  standalone: true,
  selector: 'app-alarms-table',
  templateUrl: './alarm-table.component.html',
  styleUrl: './alarm-table.component.css',
  imports: [
    AgoComponent,
    AlarmLevelComponent,
    AlarmStateIconComponent,
    WebappSdkModule,
  ],
})
export class AlarmsTableComponent {

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
  view: 'standard' | 'unacknowledged' | 'acknowledged' | 'shelved' | 'all' = 'standard';

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

  constructor(readonly yamcs: YamcsService) {
  }

  toggleOne(row: Alarm) {
    if (!this.selection.isSelected(row) || this.selection.selected.length > 1) {
      this.selection.clear();
    }
    this.selection.toggle(row);
  }

  hideAlarm(alarm: Alarm) {
    if (this.view === 'standard') {
      return !!alarm.shelveInfo;
    } else if (this.view === 'all') {
      return false;
    } else if (this.view === 'unacknowledged') {
      return !!alarm.shelveInfo || alarm.acknowledged;
    } else if (this.view === 'acknowledged') {
      return !!alarm.shelveInfo || !alarm.acknowledged;
    } else if (this.view === 'shelved') {
      return !alarm.shelveInfo;
    }
  }
}
