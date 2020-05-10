import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { TransferSubscription } from '../client';
import { YamcsService } from '../core/services/YamcsService';
import { TransferItem } from './TransferItem';

@Component({
  templateUrl: './OngoingTransfersTab.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OngoingTransfersTab implements OnDestroy {

  dataSource = new MatTableDataSource<TransferItem>();

  private transfersById = new Map<number, TransferItem>();
  private transferSubscription: TransferSubscription;

  constructor(yamcs: YamcsService) {
    const storageClient = yamcs.createStorageClient();
    this.transferSubscription = yamcs.yamcsClient.createTransferSubscription({ instance: yamcs.instance! }, transfer => {
      switch (transfer.state) {
        case 'RUNNING':
        case 'PAUSED':
          this.transfersById.set(transfer.id, {
            ...transfer,
            objectUrl: storageClient.getObjectURL(
              '_global', transfer.bucket, transfer.objectName),
          });
          break;
        case 'FAILED':
        case 'COMPLETED':
          this.transfersById.delete(transfer.id);
          break;
      }

      const values = [...this.transfersById.values()];
      values.sort((a, b) => a.startTime.localeCompare(b.startTime));
      this.dataSource.data = values;
    });
  }

  ngOnDestroy() {
    if (this.transferSubscription) {
      this.transferSubscription.cancel();
    }
  }
}
