import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  Output,
} from '@angular/core';
import {
  BaseComponent,
  ParameterValue,
  WebappSdkModule,
} from '@yamcs/webapp-sdk';
import { ParameterDataDataSource } from '../parameter-data-tab/parameter-data.datasource';

@Component({
  selector: 'app-parameter-values-table',
  templateUrl: './parameter-values-table.component.html',
  styleUrl: './parameter-values-table.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [WebappSdkModule],
})
export class ParameterValuesTableComponent extends BaseComponent {
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

  selectValue(value: ParameterValue) {
    this.selectedValue.emit(value);
    this.openDetailPane();
  }
}
