import { AfterViewInit, ChangeDetectionStrategy, Component, OnDestroy, ViewChild } from '@angular/core';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { Subscription } from 'rxjs';
import { SessionInfo } from '../../client';
import { Synchronizer } from '../../core/services/Synchronizer';
import { YamcsService } from '../../core/services/YamcsService';
import { TrackBySelectionModel } from '../../shared/table/TrackBySelectionModel';

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
    'select',
    'id',
    'user',
    'ipAddress',
    'hostname',
    'started',
    'lastAccessTime',
    'expirationTime',
    'actions',
  ];

  tableTrackerFn = (index: number, session: SessionInfo) => session.id;

  dataSource = new MatTableDataSource<SessionInfo>();
  selection = new TrackBySelectionModel<SessionInfo>(this.tableTrackerFn, true, []);

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

  isAllSelected() {
    const numSelected = this.selection.selected.length;
    const numRows = this.dataSource.data.length;
    return numSelected === numRows;
  }

  masterToggle() {
    this.isAllSelected() ?
      this.selection.clear() :
      this.dataSource.data.forEach(row => this.selection.select(row));
  }

  toggleOne(row: SessionInfo) {
    if (!this.selection.isSelected(row) || this.selection.selected.length > 1) {
      this.selection.clear();
    }
    this.selection.toggle(row);
  }

  private refresh() {
    this.yamcs.yamcsClient.getSessions().then(sessions => {
      this.selection.matchNewValues(sessions || []);
      this.dataSource.data = sessions || [];
    });
  }

  closeSelectedConnections() {
    for (const connection of this.selection.selected) {
      this.closeConnection(connection.id);
    }
  }

  closeConnection(id: string) {
    this.yamcs.yamcsClient.closeClientConnection(id).then(() => this.refresh());
  }

  isGroupCloseEnabled() {
    return !this.selection.isEmpty();
  }

  ngOnDestroy() {
    if (this.syncSubscription) {
      this.syncSubscription.unsubscribe();
    }
  }
}
