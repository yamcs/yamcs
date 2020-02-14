import { SelectionModel } from '@angular/cdk/collections';
import { AfterViewInit, ChangeDetectionStrategy, Component, ViewChild } from '@angular/core';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { ClientConnectionInfo } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './ConnectionsPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConnectionsPage implements AfterViewInit {

  @ViewChild(MatSort)
  sort: MatSort;

  @ViewChild(MatPaginator)
  paginator: MatPaginator;

  displayedColumns = [
    'select',
    'id',
    // 'userAgent',
    'protocol',
    'remoteAddress',
    'readBytes',
    'writtenBytes',
    'readThroughput',
    'writeThroughput',
    'request',
    'actions',
  ];

  dataSource = new MatTableDataSource<ClientConnectionInfo>();
  selection = new SelectionModel<ClientConnectionInfo>(true, []);

  constructor(private yamcs: YamcsService, title: Title) {
    title.setTitle('Client connections');
    this.refresh();
  }

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
    this.dataSource.paginator = this.paginator;
  }

  // trackBy is needed to prevent menu from closing when the queue object is updated
  tableTrackerFn = (index: number, conn: ClientConnectionInfo) => conn.id;

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

  toggleOne(row: ClientConnectionInfo) {
    if (!this.selection.isSelected(row) || this.selection.selected.length > 1) {
      this.selection.clear();
    }
    this.selection.toggle(row);
  }

  refresh() {
    this.selection.clear();
    this.yamcs.yamcsClient.getClientConnections().then(conns => {
      this.dataSource.data = conns || [];
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
}
