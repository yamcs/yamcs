import { Component, ChangeDetectionStrategy, ViewChild, Input, AfterViewInit } from '@angular/core';

import { Algorithm } from '../../../yamcs-client';

import { MatSort, MatTableDataSource } from '@angular/material';
import { Observable } from 'rxjs/Observable';

@Component({
  selector: 'app-algorithms-table',
  templateUrl: './AlgorithmsTable.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlgorithmsTable implements AfterViewInit {

  @Input()
  instance: string;

  @Input()
  algorithms$: Observable<Algorithm[]>;

  @Input()
  shortName = false;

  @ViewChild(MatSort)
  sort: MatSort;

  dataSource = new MatTableDataSource<Algorithm>([]);

  displayedColumns = ['name', 'language', 'scope', 'shortDescription'];

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
    this.algorithms$.subscribe(algorithms => {
      this.dataSource.data = algorithms || [];
    });
  }

  applyFilter(value: string) {
    this.dataSource.filter = value.trim().toLowerCase();
  }
}
