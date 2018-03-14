import { Component, ChangeDetectionStrategy, ViewChild, Input, AfterViewInit } from '@angular/core';

import { Command } from '@yamcs/client';

import { MatSort, MatTableDataSource } from '@angular/material';

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

  dataSource = new MatTableDataSource<Command>([]);

  displayedColumns = ['name', 'shortDescription'];

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
    this.commands$.then(commands => {
      this.dataSource.data = commands;
    });
  }

  applyFilter(value: string) {
    this.dataSource.filter = value.trim().toLowerCase();
  }
}
