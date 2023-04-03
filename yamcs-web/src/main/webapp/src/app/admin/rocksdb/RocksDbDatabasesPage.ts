import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatLegacyTableDataSource } from '@angular/material/legacy-table';
import { Title } from '@angular/platform-browser';
import { RocksDbDatabase } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';

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

  dataSource = new MatLegacyTableDataSource<RocksDbDatabase>();

  constructor(yamcs: YamcsService, title: Title) {
    title.setTitle('Open Databases');
    yamcs.yamcsClient.getRocksDbDatabases().then(databases => {
      this.dataSource.data = databases;
    });
  }
}
