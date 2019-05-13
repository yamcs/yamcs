import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatTableDataSource } from '@angular/material';
import { Title } from '@angular/platform-browser';
import { RocksDbDatabase } from '@yamcs/client';
import { YamcsService } from '../core/services/YamcsService';

@Component({
  templateUrl: './DatabasesPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DatabasesPage {

  displayedColumns = [
    'name',
    'tablespace',
    'dataDir',
    'dbPath',
  ];

  dataSource = new MatTableDataSource<RocksDbDatabase>();

  constructor(yamcs: YamcsService, title: Title) {
    title.setTitle('Open Databases');
    yamcs.yamcsClient.getRocksDbDatabases().then(databases => {
      this.dataSource.data = databases;
    });
  }
}
