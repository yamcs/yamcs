import { Component, ChangeDetectionStrategy, ViewChild, Input, AfterViewInit } from '@angular/core';

import { Algorithm } from '../../../yamcs-client';

import { MatSort, MatTableDataSource } from '@angular/material';
import { Observable } from 'rxjs/Observable';

@Component({
  selector: 'app-algorithms-table',
  templateUrl: './algorithms-table.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlgorithmsTableComponent implements AfterViewInit {

  @Input()
  algorithms$: Observable<Algorithm[]>;

  @ViewChild(MatSort)
  sort: MatSort;

  dataSource = new MatTableDataSource<Algorithm>();

  displayedColumns = ['name', 'language', 'scope', 'shortDescription'];

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
    this.algorithms$.subscribe(algorithms => {
      this.dataSource.data = algorithms;
    });
  }
}
