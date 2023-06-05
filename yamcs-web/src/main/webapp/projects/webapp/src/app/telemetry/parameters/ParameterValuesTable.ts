import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { ParameterValue } from '@yamcs/webapp-sdk';
import { ParameterDataDataSource } from './ParameterDataDataSource';

@Component({
  selector: 'app-parameter-values-table',
  templateUrl: './ParameterValuesTable.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParameterValuesTable {

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
