import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { RocksDbDatabase } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './DatabasesPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DatabasesPage {

  displayedColumns = [
    'dataDir',
    'tablespace',
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
