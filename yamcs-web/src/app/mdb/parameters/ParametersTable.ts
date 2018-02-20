import { Component, ChangeDetectionStrategy, ViewChild, Input, AfterViewInit } from '@angular/core';

import { Parameter } from '../../../yamcs-client';

import { MatSort, MatTableDataSource } from '@angular/material';
import { Observable } from 'rxjs/Observable';

@Component({
  selector: 'app-parameters-table',
  templateUrl: './ParametersTable.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParametersTable implements AfterViewInit {

  @Input()
  instance: string;

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

  applyFilter(value: string) {
    this.dataSource.filter = value.trim().toLowerCase();
  }
}
