import { Component, ChangeDetectionStrategy, ViewChild, Input, AfterViewInit } from '@angular/core';

import { Command } from '../../../yamcs-client';

import { MatSort, MatTableDataSource } from '@angular/material';
import { Observable } from 'rxjs/Observable';

@Component({
  selector: 'app-commands-table',
  templateUrl: './commands-table.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandsTableComponent implements AfterViewInit {

  @Input()
  commands$: Observable<Command[]>;

  @Input()
  shortName = false;

  @ViewChild(MatSort)
  sort: MatSort;

  dataSource = new MatTableDataSource<Command>([]);

  displayedColumns = ['name', 'shortDescription'];

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
    this.commands$.subscribe(commands => {
      this.dataSource.data = commands || [];
    });
  }
}
