import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { ReplicationInfoSubscription, ReplicationMaster, ReplicationSlave, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { AdminPageTemplateComponent } from '../../shared/admin-page-template/admin-page-template.component';
import { AdminToolbarComponent } from '../../shared/admin-toolbar/admin-toolbar.component';
import { ReplicationStateComponent } from '../replication-state/replication-state.component';
import { ShowStreamsDialogComponent } from '../show-streams-dialog/show-streams-dialog.component';

@Component({
  standalone: true,
  templateUrl: './replication.component.html',
  styleUrl: './replication.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AdminPageTemplateComponent,
    AdminToolbarComponent,
    ReplicationStateComponent,
    WebappSdkModule,
  ],
})
export class ReplicationComponent implements OnDestroy {

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
    this.dialog.open(ShowStreamsDialogComponent, {
      width: '500px',
      data: { streams },
    });
  }

  ngOnDestroy() {
    this.replicationInfoSubscription?.cancel();
  }
}
