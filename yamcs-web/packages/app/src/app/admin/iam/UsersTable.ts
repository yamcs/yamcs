import { AfterViewInit, ChangeDetectionStrategy, Component, Input, OnChanges, ViewChild } from '@angular/core';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { UserInfo } from '@yamcs/client';

@Component({
  selector: 'app-users-table',
  templateUrl: './UsersTable.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UsersTable implements AfterViewInit, OnChanges {

  displayedColumns = [
    'name',
    'displayName',
    'groups',
    // 'registered',
    // 'lastLogin',
    'actions',
  ];

  @Input()
  users: UserInfo[];

  @Input()
  filter: string;

  @ViewChild(MatSort, { static: false })
  sort: MatSort;

  dataSource = new MatTableDataSource<UserInfo>();

  ngAfterViewInit() {
    this.dataSource.filterPredicate = (user, filter) => {
      return user.name.toLowerCase().indexOf(filter) >= 0;
    };
    this.dataSource.sort = this.sort;
  }

  ngOnChanges() {
    this.dataSource.data = this.users || [];
    this.dataSource.filter = this.filter;
  }
}
