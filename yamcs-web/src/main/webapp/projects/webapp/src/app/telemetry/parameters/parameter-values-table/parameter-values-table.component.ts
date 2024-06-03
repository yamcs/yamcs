import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { ParameterValue, WebappSdkModule } from '@yamcs/webapp-sdk';
import { AlarmLevelComponent } from '../../../shared/alarm-level/alarm-level.component';
import { ParameterDataDataSource } from '../parameter-data-tab/parameter-data.datasource';

@Component({
  standalone: true,
  selector: 'app-parameter-values-table',
  templateUrl: './parameter-values-table.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AlarmLevelComponent,
    WebappSdkModule,
  ],
})
export class ParameterValuesTableComponent {

  @Input()
  dataSource: ParameterDataDataSource;

  @Output()
  selectedValue = new EventEmitter<ParameterValue>();

  displayedColumns = [
    'severity',
    'generationTime',
    // 'receptionTime', // Only works for pcache, not parchive.
    'rawValue',
    'engValue',
    'rangeCondition',
    'acquisitionStatus',
    'actions',
  ];
}
