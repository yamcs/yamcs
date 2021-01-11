import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { ReplicationInfoSubscription, ReplicationMaster, ReplicationSlave } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './ReplicationPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReplicationPage implements OnDestroy {

  slaveColumns = [
    'state',
    'instance',
    'streams',
    'mode',
    'localAddress',
    'remoteAddress',
    'pullFrom',
    'tx',
  ];

  masterColumns = [
    'state',
    'instance',
    'streams',
    'mode',
    'localAddress',
    'remoteAddress',
    'pushTo',
    'localTx',
    'nextTx',
  ];

  slavesDataSource = new MatTableDataSource<ReplicationSlave>();
  mastersDataSource = new MatTableDataSource<ReplicationMaster>();

  private replicationInfoSubscription: ReplicationInfoSubscription;

  constructor(
    yamcs: YamcsService,
    title: Title,
  ) {
    title.setTitle('Replication');
    this.replicationInfoSubscription = yamcs.yamcsClient.createReplicationInfoSubscription(info => {
      this.slavesDataSource.data = info.slaves || [];
      this.mastersDataSource.data = info.masters || [];
    });
  }

  ngOnDestroy() {
    if (this.replicationInfoSubscription) {
      this.replicationInfoSubscription.cancel();
    }
  }
}
