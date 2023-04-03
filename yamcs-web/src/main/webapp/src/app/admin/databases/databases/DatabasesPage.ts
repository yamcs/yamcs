import { AfterViewInit, ChangeDetectionStrategy, Component, ViewChild } from '@angular/core';
import { MatLegacyTableDataSource } from '@angular/material/legacy-table';
import { MatSort } from '@angular/material/sort';
import { Title } from '@angular/platform-browser';
import { Database } from '../../../client';
import { MessageService } from '../../../core/services/MessageService';
import { YamcsService } from '../../../core/services/YamcsService';

@Component({
  templateUrl: './DatabasesPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DatabasesPage implements AfterViewInit {

  @ViewChild(MatSort, { static: true })
  sort: MatSort;

  displayedColumns = ['name', 'tablespace', 'path', 'actions'];

  dataSource = new MatLegacyTableDataSource<Database>();

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
