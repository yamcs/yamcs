import { AfterViewInit, ChangeDetectionStrategy, Component, ViewChild } from '@angular/core';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { Table } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';



@Component({
  templateUrl: './TablesPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TablesPage implements AfterViewInit {

  @ViewChild(MatSort, { static: true })
  sort: MatSort;

  instance: string;

  displayedColumns = ['name'];

  dataSource = new MatTableDataSource<Table>();

  constructor(yamcs: YamcsService, title: Title) {
    title.setTitle('Tables');
    this.instance = yamcs.getInstance();
    yamcs.yamcsClient.getTables(this.instance).then(tables => {
      this.dataSource.data = tables;
    });
  }

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
  }
}
