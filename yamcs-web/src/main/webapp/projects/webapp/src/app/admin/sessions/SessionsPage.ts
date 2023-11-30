import { AfterViewInit, ChangeDetectionStrategy, Component, OnDestroy, ViewChild } from '@angular/core';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { SessionInfo, Synchronizer, YamcsService } from '@yamcs/webapp-sdk';
import { Subscription } from 'rxjs';

@Component({
  templateUrl: './SessionsPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SessionsPage implements AfterViewInit, OnDestroy {

  @ViewChild(MatSort)
  sort: MatSort;

  @ViewChild(MatPaginator)
  paginator: MatPaginator;

  displayedColumns = [
    'id',
    'user',
    'ipAddress',
    'hostname',
    'started',
    'lastAccessTime',
    'expirationTime',
    'clients',
  ];

  tableTrackerFn = (index: number, session: SessionInfo) => session.id;

  dataSource = new MatTableDataSource<SessionInfo>();

  private syncSubscription: Subscription;

  constructor(
    private yamcs: YamcsService,
    title: Title,
    synchronizer: Synchronizer,
  ) {
    title.setTitle('Sessions');

    this.refresh();
    this.syncSubscription = synchronizer.syncSlow(() => this.refresh());
  }

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
    this.dataSource.paginator = this.paginator;
  }

  private refresh() {
    this.yamcs.yamcsClient.getSessions().then(sessions => {
      this.dataSource.data = sessions || [];
    });
  }

  ngOnDestroy() {
    if (this.syncSubscription) {
      this.syncSubscription.unsubscribe();
    }
  }
}
