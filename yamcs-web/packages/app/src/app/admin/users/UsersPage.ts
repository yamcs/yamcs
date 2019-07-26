import { AfterViewInit, ChangeDetectionStrategy, Component, ViewChild } from '@angular/core';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { UserInfo } from '@yamcs/client';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './UsersPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UsersPage implements AfterViewInit {

  @ViewChild(MatSort, { static: true })
  sort: MatSort;

  displayedColumns = [
    'login',
    'superuser',
  ];
  dataSource = new MatTableDataSource<UserInfo>();

  constructor(private yamcs: YamcsService, title: Title) {
    title.setTitle('Users');
    this.refreshDataSources();
  }

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
  }

  private refreshDataSources() {
    this.yamcs.yamcsClient.getUsers().then(users => {
      this.dataSource.data = users;
    });
  }
}
