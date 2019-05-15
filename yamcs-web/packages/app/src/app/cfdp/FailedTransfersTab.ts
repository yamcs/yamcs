import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { MatTableDataSource } from '@angular/material';
import { Transfer } from '@yamcs/client';
import { Subscription } from 'rxjs';
import { Synchronizer } from '../core/services/Synchronizer';
import { YamcsService } from '../core/services/YamcsService';

@Component({
  templateUrl: './FailedTransfersTab.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FailedTransfersTab implements OnDestroy {

  dataSource = new MatTableDataSource<Transfer>();

  private syncSubscription: Subscription;

  constructor(private yamcs: YamcsService, synchronizer: Synchronizer) {
    this.refresh();
    this.syncSubscription = synchronizer.sync(() => this.refresh());
  }

  private refresh() {
    this.yamcs.getInstanceClient()!.getCfdpTransfers().then(page => {
      this.dataSource.data = (page.transfers || [])
        .filter(t => t.state === 'FAILED');
    });
  }

  ngOnDestroy() {
    if (this.syncSubscription) {
      this.syncSubscription.unsubscribe();
    }
  }
}
