import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { TransferSubscription } from '../client';
import { YamcsService } from '../core/services/YamcsService';
import { TransferItem } from './TransferItem';

@Component({
  templateUrl: './SuccessfulTransfersTab.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SuccessfulTransfersTab implements OnDestroy {

  dataSource = new MatTableDataSource<TransferItem>();

  private transfersById = new Map<number, TransferItem>();
  private transferSubscription: TransferSubscription;

  constructor(yamcs: YamcsService) {
    const storageClient = yamcs.createStorageClient();
    this.transferSubscription = yamcs.yamcsClient.createTransferSubscription({ instance: yamcs.instance! }, transfer => {
      switch (transfer.state) {
        case 'COMPLETED':
          this.transfersById.set(transfer.id, {
            ...transfer,
            objectUrl: storageClient.getObjectURL(
              '_global', transfer.bucket, transfer.objectName),
          });
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
