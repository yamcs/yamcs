import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { RocksDbDatabase, YamcsService } from '@yamcs/webapp-sdk';

@Component({
  templateUrl: './RocksDbDatabasesPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RocksDbDatabasesPage {

  displayedColumns = [
    'dataDir',
    'tablespace',
    'dbPath',
    'actions',
  ];

  dataSource = new MatTableDataSource<RocksDbDatabase>();

  constructor(yamcs: YamcsService, title: Title) {
    title.setTitle('Open Databases');
    yamcs.yamcsClient.getRocksDbDatabases().then(databases => {
      this.dataSource.data = databases;
    });
  }
}
