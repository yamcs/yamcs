import { Component, ChangeDetectionStrategy, ViewChild, AfterViewInit } from '@angular/core';

import { ClientInfo } from '../../../yamcs-client';

import { YamcsService } from '../../core/services/yamcs.service';
import { MatTableDataSource, MatSort } from '@angular/material';

@Component({
  templateUrl: './clients.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClientsPageComponent implements AfterViewInit {

  @ViewChild(MatSort)
  sort: MatSort;

  displayedColumns = ['id', 'username', 'applicationName', 'processorName', 'loginTime'];

  dataSource = new MatTableDataSource<ClientInfo>();

  private clientsById: { [key: string]: ClientInfo } = {};

  constructor(yamcs: YamcsService) {
    yamcs.getSelectedInstance().getClientUpdates().subscribe(evt => {
      this.processClientEvent(evt);
    });
  }

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
  }

  private processClientEvent(evt: ClientInfo) {
    switch (evt.state) {
      case 'CONNECTED':
        this.clientsById[evt.id] = evt;
        this.dataSource.data = Object.values(this.clientsById);
        break;
      case 'DISCONNECTED':
        delete this.clientsById[evt.id];
        this.dataSource.data = Object.values(this.clientsById);
        break;
      default:
        console.error('Unexpected client state ' + evt.state);
        break;
    }
  }
}
