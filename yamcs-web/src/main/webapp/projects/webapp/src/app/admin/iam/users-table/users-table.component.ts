import { AfterViewInit, ChangeDetectionStrategy, Component, EventEmitter, Input, OnChanges, Output, ViewChild } from '@angular/core';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { UserInfo, WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-users-table',
  templateUrl: './users-table.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class UsersTableComponent implements AfterViewInit, OnChanges {

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
