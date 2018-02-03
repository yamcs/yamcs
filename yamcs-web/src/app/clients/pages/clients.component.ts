import { Component, ChangeDetectionStrategy } from '@angular/core';

import { ClientInfo } from '../../../yamcs-client';

import { BehaviorSubject } from 'rxjs/BehaviorSubject';
import { YamcsService } from '../../core/services/yamcs.service';

@Component({
  templateUrl: './clients.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClientsPageComponent {

  clients$ = new BehaviorSubject<ClientInfo[]>([]);

  private clientsById = new Map<number, ClientInfo>();

  constructor(yamcs: YamcsService) {
    yamcs.getSelectedInstance().getClientUpdates().subscribe(evt => {
      this.processClientEvent(evt);
    });
  }

  private processClientEvent(evt: ClientInfo) {
    switch (evt.state) {
      case 'CONNECTED':
        this.clientsById.set(evt.id, evt);
        this.sortAndEmitClients();
        break;
      case 'DISCONNECTED':
        this.clientsById.delete(evt.id);
        this.sortAndEmitClients();
        break;
      default:
        console.error('Unexpected client state ' + evt.state);
        break;
    }
  }

  private sortAndEmitClients() {
    const clients: ClientInfo[] = [];
    this.clientsById.forEach(client => {
      clients.push(client);
    });
    this.clients$.next(clients.sort((a, b) => {
      if (a.username < b.username) {
        return -1;
      } else if (a.username > b.username) {
        return 1;
      } else {
        return 0;
      }
    }));
  }
}
