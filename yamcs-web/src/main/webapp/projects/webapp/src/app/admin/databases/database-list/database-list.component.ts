import { AfterViewInit, ChangeDetectionStrategy, Component, ViewChild } from '@angular/core';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { Database, MessageService, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { AdminPageTemplateComponent } from '../../shared/admin-page-template/admin-page-template.component';
import { AdminToolbarComponent } from '../../shared/admin-toolbar/admin-toolbar.component';

@Component({
  standalone: true,
  templateUrl: './database-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AdminPageTemplateComponent,
    AdminToolbarComponent,
    WebappSdkModule,
  ],
})
export class DatabaseListComponent implements AfterViewInit {

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
