import { AfterViewInit, ChangeDetectionStrategy, Component, ViewChild } from '@angular/core';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { Instance, Table } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';



@Component({
  templateUrl: './TablesPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TablesPage implements AfterViewInit {

  @ViewChild(MatSort, { static: true })
  sort: MatSort;

  instance: Instance;

  displayedColumns = ['name'];

  dataSource = new MatTableDataSource<Table>();

  constructor(yamcs: YamcsService, title: Title) {
    title.setTitle('Tables');
    yamcs.getInstanceClient()!.getTables().then(tables => {
      this.dataSource.data = tables;
    });
    this.instance = yamcs.getInstance();
  }

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
  }
}
