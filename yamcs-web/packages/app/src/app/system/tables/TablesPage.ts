import { Component, ChangeDetectionStrategy, ViewChild, AfterViewInit } from '@angular/core';

import { Table, Instance } from '@yamcs/client';

import { YamcsService } from '../../core/services/YamcsService';
import { MatTableDataSource, MatSort } from '@angular/material';
import { Title } from '@angular/platform-browser';

@Component({
  templateUrl: './TablesPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TablesPage implements AfterViewInit {

  @ViewChild(MatSort)
  sort: MatSort;

  instance: Instance;

  displayedColumns = ['name'];

  dataSource = new MatTableDataSource<Table>();

  constructor(yamcs: YamcsService, title: Title) {
    title.setTitle('Tables - Yamcs');
    yamcs.getInstanceClient()!.getTables().then(tables => {
      this.dataSource.data = tables;
    });
    this.instance = yamcs.getInstance();
  }

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
  }
}
