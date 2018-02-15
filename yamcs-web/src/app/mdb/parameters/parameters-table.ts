import { Component, ChangeDetectionStrategy, ViewChild, Input, AfterViewInit } from '@angular/core';

import { Parameter } from '../../../yamcs-client';

import { MatSort, MatTableDataSource } from '@angular/material';
import { Observable } from 'rxjs/Observable';

@Component({
  selector: 'app-parameters-table',
  templateUrl: './parameters-table.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParametersTableComponent implements AfterViewInit {

  @Input()
  parameters$: Observable<Parameter[]>;

  @Input()
  shortName = false;

  @ViewChild(MatSort)
  sort: MatSort;

  dataSource = new MatTableDataSource<Parameter>([]);

  displayedColumns = ['name', 'type', 'units', 'dataSource', 'shortDescription'];

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
    this.parameters$.subscribe(parameters => {
      this.dataSource.data = parameters || [];
    });
  }
}
