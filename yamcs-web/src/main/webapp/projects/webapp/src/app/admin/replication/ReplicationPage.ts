import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { ReplicationInfoSubscription, ReplicationMaster, ReplicationSlave, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { ShowStreamsDialog } from './ShowStreamsDialog';

@Component({
  templateUrl: './ReplicationPage.html',
  styleUrls: ['./ReplicationPage.css'],
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
    'actions',
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
    'actions',
  ];

  slavesDataSource = new MatTableDataSource<ReplicationSlave>();
  mastersDataSource = new MatTableDataSource<ReplicationMaster>();
  hasSlaves$ = new BehaviorSubject<boolean>(false);
  hasMasters$ = new BehaviorSubject<boolean>(false);

  private replicationInfoSubscription: ReplicationInfoSubscription;

  constructor(
    yamcs: YamcsService,
    title: Title,
    private dialog: MatDialog,
  ) {
    title.setTitle('Replication');
    this.replicationInfoSubscription = yamcs.yamcsClient.createReplicationInfoSubscription(info => {
      this.slavesDataSource.data = info.slaves || [];
      this.mastersDataSource.data = info.masters || [];
      this.hasSlaves$.next(this.slavesDataSource.data.length > 0);
      this.hasMasters$.next(this.mastersDataSource.data.length > 0);
    });
  }

  showReplicationStreams(streams: string) {
    this.dialog.open(ShowStreamsDialog, {
      width: '500px',
      data: { streams },
    });
  }

  ngOnDestroy() {
    this.replicationInfoSubscription?.cancel();
  }
}
