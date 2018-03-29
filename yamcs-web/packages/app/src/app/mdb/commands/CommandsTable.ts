import { Component, ChangeDetectionStrategy, ViewChild, Input, AfterViewInit } from '@angular/core';

import { Command } from '@yamcs/client';

import { MatSort, MatTableDataSource, MatPaginator } from '@angular/material';
import { ColumnInfo } from '../../shared/template/ColumnChooser';

@Component({
  selector: 'app-commands-table',
  templateUrl: './CommandsTable.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandsTable implements AfterViewInit {

  @Input()
  instance: string;

  @Input()
  commands$: Promise<Command[]>;

  @Input()
  shortName = false;

  @ViewChild(MatSort)
  sort: MatSort;

  @ViewChild(MatPaginator)
  paginator: MatPaginator;

  dataSource = new MatTableDataSource<Command>([]);

  columns: ColumnInfo[] = [
    { id: 'name', label: 'Name', alwaysVisible: true },
    { id: 'shortDescription', label: 'Description' },
  ];

  displayedColumns = ['name', 'shortDescription'];

  ngAfterViewInit() {
    this.dataSource.paginator = this.paginator;
    this.dataSource.sort = this.sort;
    this.commands$.then(commands => {
      this.dataSource.data = commands;
    });
  }

  applyFilter(value: string) {
    this.dataSource.filter = value.trim().toLowerCase();
  }

  updateColumns(displayedColumns: string[]) {
    this.displayedColumns = displayedColumns;
  }
}
