import { Component, ChangeDetectionStrategy, ViewChild, AfterViewInit } from '@angular/core';

import { ClientInfo } from '../../../yamcs-client';

import { YamcsService } from '../../core/services/YamcsService';
import { MatTableDataSource, MatSort } from '@angular/material';

@Component({
  templateUrl: './ClientsPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClientsPage implements AfterViewInit {

  @ViewChild(MatSort)
  sort: MatSort;

  displayedColumns = ['id', 'username', 'applicationName', 'processorName', 'loginTime'];

  dataSource = new MatTableDataSource<ClientInfo>();

  private clientsById: { [key: string]: ClientInfo } = {};

  constructor(yamcs: YamcsService) {
    yamcs.getSelectedInstance().getClients().subscribe(clients => {
      for (const client of clients) {
        this.processClientEvent(client);
      }
    });

    yamcs.getSelectedInstance().getClientUpdates().subscribe(evt => {
      this.processClientEvent(evt);
    });
  }

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
  }

  private processClientEvent(evt: ClientInfo) {
    switch (evt.state) {
      case undefined:
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
