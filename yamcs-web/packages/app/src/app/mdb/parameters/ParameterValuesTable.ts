import { Component, ChangeDetectionStrategy, Input, AfterViewInit } from '@angular/core';

import { ParameterValue } from '@yamcs/client';

import { MatTableDataSource } from '@angular/material';
import { Observable } from 'rxjs/Observable';

@Component({
  selector: 'app-parameter-values-table',
  templateUrl: './ParameterValuesTable.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParameterValuesTable implements AfterViewInit {

  @Input()
  parameterValues$: Observable<ParameterValue[]>;

  dataSource = new MatTableDataSource<ParameterValue>([]);

  displayedColumns = [
    'severity',
    'generationTimeUTC',
    'receptionTimeUTC',
    'rawValue',
    'engValue',
    'rangeCondition',
    'acquisitionStatus',
  ];

  ngAfterViewInit() {
    this.parameterValues$.subscribe(pvals => {
      this.dataSource.data = pvals;
    });
  }
}
