import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { RocksDbDatabase, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { AdminPageTemplateComponent } from '../../shared/admin-page-template/admin-page-template.component';
import { AdminToolbarComponent } from '../../shared/admin-toolbar/admin-toolbar.component';

@Component({
  standalone: true,
  templateUrl: './rocksdb-database-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AdminPageTemplateComponent,
    AdminToolbarComponent,
    WebappSdkModule,
  ],
})
export class RocksDbDatabasesComponent {

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
