import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { ParameterAlarmsDataSource } from './ParameterAlarmsDataSource';

@Component({
  selector: 'app-parameter-alarms-table',
  templateUrl: './ParameterAlarmsTable.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParameterAlarmsTable {

  @Input()
  dataSource: ParameterAlarmsDataSource;

  displayedColumns = [
    'severity',
    'triggerTime',
    'triggerValue',
  ];
}
