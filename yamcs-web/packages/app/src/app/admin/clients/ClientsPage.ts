import { AfterViewInit, ChangeDetectionStrategy, Component, OnDestroy, ViewChild } from '@angular/core';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { ClientInfo } from '@yamcs/client';
import { Subscription } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './ClientsPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClientsPage implements AfterViewInit, OnDestroy {

  @ViewChild(MatSort, { static: true })
  sort: MatSort;

  displayedColumns = [
    'id',
    'username',
    'applicationName',
    'address',
    // 'instance',
    // 'processorName',
    'loginTime',
  ];

  dataSource = new MatTableDataSource<ClientInfo>();

  clientSubscription: Subscription;

  private clientsById: { [key: string]: ClientInfo } = {};

  constructor(private yamcs: YamcsService, title: Title) {
    title.setTitle('Clients');

    this.yamcs.yamcsClient.getClientUpdates().then(response => {
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
