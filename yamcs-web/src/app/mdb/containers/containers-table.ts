import { Component, ChangeDetectionStrategy, ViewChild, Input, AfterViewInit } from '@angular/core';

import { Container } from '../../../yamcs-client';

import { MatSort, MatTableDataSource } from '@angular/material';
import { Observable } from 'rxjs/Observable';

@Component({
  selector: 'app-containers-table',
  templateUrl: './containers-table.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ContainersTableComponent implements AfterViewInit {

  @Input()
  containers$: Observable<Container[]>;

  @Input()
  shortName = false;

  @ViewChild(MatSort)
  sort: MatSort;

  dataSource = new MatTableDataSource<Container>([]);

  displayedColumns = ['name', 'maxInterval', 'sizeInBits', 'baseContainer', 'restrictionCriteria', 'shortDescription'];

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
    this.containers$.subscribe(containers => {
      this.dataSource.data = containers || [];
    });
  }
}
