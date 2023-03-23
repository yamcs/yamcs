import { AfterViewInit, ChangeDetectionStrategy, Component, OnDestroy, ViewChild } from '@angular/core';
import { MatLegacyPaginator } from '@angular/material/legacy-paginator';
import { MatLegacyTableDataSource } from '@angular/material/legacy-table';
import { MatSort } from '@angular/material/sort';
import { Title } from '@angular/platform-browser';
import { Subscription } from 'rxjs';
import { SessionInfo } from '../../client';
import { Synchronizer } from '../../core/services/Synchronizer';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './SessionsPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SessionsPage implements AfterViewInit, OnDestroy {

  @ViewChild(MatSort)
  sort: MatSort;

  @ViewChild(MatLegacyPaginator)
  paginator: MatLegacyPaginator;

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

  dataSource = new MatLegacyTableDataSource<SessionInfo>();

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
