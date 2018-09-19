import { AfterViewInit, ChangeDetectionStrategy, Component, OnDestroy, ViewChild } from '@angular/core';
import { MatButtonToggleGroup, MatSort, MatTableDataSource } from '@angular/material';
import { Title } from '@angular/platform-browser';
import { ClientInfo } from '@yamcs/client';
import { Subscription } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';



@Component({
  templateUrl: './ClientsPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClientsPage implements AfterViewInit, OnDestroy {

  @ViewChild(MatSort)
  sort: MatSort;

  displayedColumns = ['id', 'username', 'applicationName', 'instance', 'processorName', 'loginTime'];

  dataSource = new MatTableDataSource<ClientInfo>();

  clientSubscription: Subscription;

  @ViewChild(MatButtonToggleGroup)
  group: MatButtonToggleGroup;

  private clientsById: { [key: string]: ClientInfo } = {};

  constructor(private yamcs: YamcsService, title: Title) {
    title.setTitle('Clients - Yamcs');
    this.yamcs.yamcsClient.getClientUpdates().then(response => {
      this.clientSubscription = response.client$.subscribe(evt => {
        this.processClientEvent(evt);
      });
    });

    this.dataSource.filterPredicate = (client, filter) => {
      const currentInstance = this.yamcs.getInstance().name;
      const v = filter === 'all' || client.instance === currentInstance;
      console.log('err', v);
      return v;
    };
  }

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
    this.dataSource.filter = 'current';
  }

  applyFilter() {
    this.dataSource.filter = this.group.value;
  }

  ngOnDestroy() {
    if (this.clientSubscription) {
      this.clientSubscription.unsubscribe();
    }
  }

  private processClientEvent(evt: ClientInfo) {
    console.log('process', evt.id, evt.state);
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
