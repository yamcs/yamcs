import { Component, ChangeDetectionStrategy, ViewChild, AfterViewInit, OnDestroy } from '@angular/core';

import { ClientInfo } from '@yamcs/client';

import { YamcsService } from '../../core/services/YamcsService';
import { MatTableDataSource, MatSort } from '@angular/material';
import { Title } from '@angular/platform-browser';
import { Subscription } from 'rxjs';

@Component({
  templateUrl: './ClientsPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClientsPage implements AfterViewInit, OnDestroy {

  @ViewChild(MatSort)
  sort: MatSort;

  displayedColumns = ['id', 'username', 'applicationName', 'processorName', 'loginTime'];

  dataSource = new MatTableDataSource<ClientInfo>();

  clientSubscription: Subscription;

  private clientsById: { [key: string]: ClientInfo } = {};

  constructor(yamcs: YamcsService, title: Title) {
    title.setTitle('Clients - Yamcs');
    yamcs.getInstanceClient()!.getClients().then(clients => {
      for (const client of clients) {
        this.processClientEvent(client);
      }
    });

    yamcs.getInstanceClient()!.getClientUpdates().then(response => {
      this.clientSubscription = response.client$.subscribe(evt => {
        this.processClientEvent(evt);
      });
    });
  }

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
  }

  ngOnDestroy() {
    if (this.clientSubscription) {
      this.clientSubscription.unsubscribe();
    }
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
