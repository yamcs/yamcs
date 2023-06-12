import { AfterViewInit, ChangeDetectionStrategy, Component, EventEmitter, Input, OnChanges, Output, ViewChild } from '@angular/core';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { UserInfo } from '@yamcs/webapp-sdk';

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

  @Output()
  deleteUser = new EventEmitter<string>();

  @ViewChild(MatSort)
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
