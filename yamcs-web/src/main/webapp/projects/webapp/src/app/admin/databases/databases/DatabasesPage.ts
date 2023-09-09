import { AfterViewInit, ChangeDetectionStrategy, Component, ViewChild } from '@angular/core';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { Database, MessageService, YamcsService } from '@yamcs/webapp-sdk';

@Component({
  templateUrl: './DatabasesPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DatabasesPage implements AfterViewInit {

  @ViewChild(MatSort, { static: true })
  sort: MatSort;

  displayedColumns = ['name', 'tablespace', 'path', 'actions'];

  dataSource = new MatTableDataSource<Database>();

  constructor(
    readonly yamcs: YamcsService,
    title: Title,
    messageService: MessageService,
  ) {
    title.setTitle('Databases');
    yamcs.yamcsClient.getDatabases().then(databases => {
      this.dataSource.data = databases;
    }).catch(err => messageService.showError(err));
  }

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
  }
}
