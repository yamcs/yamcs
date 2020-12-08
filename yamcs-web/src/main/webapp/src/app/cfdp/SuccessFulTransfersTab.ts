import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { ActivatedRoute } from '@angular/router';
import { BehaviorSubject, Subscription } from 'rxjs';
import { StorageClient, TransferSubscription } from '../client';
import { YamcsService } from '../core/services/YamcsService';
import { TransferItem } from './TransferItem';

@Component({
  templateUrl: './SuccessfulTransfersTab.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SuccessfulTransfersTab implements OnDestroy {

  serviceName$ = new BehaviorSubject<string | null>(null);
  dataSource = new MatTableDataSource<TransferItem>();

  private storageClient: StorageClient;
  private transfersById = new Map<number, TransferItem>();
  private transferSubscription: TransferSubscription;

  private queryParamSubscription: Subscription;

  constructor(private yamcs: YamcsService, route: ActivatedRoute) {
    this.storageClient = yamcs.createStorageClient();
    this.queryParamSubscription = route.queryParamMap.subscribe(params => {
      const service = params.get('service');
      this.serviceName$.next(service);
      this.switchService(service);
    });
  }

  private switchService(service: string | null) {
    // Clear state
    this.transfersById.clear();
    this.dataSource.data = [];
    if (this.transferSubscription) {
      this.transferSubscription.cancel();
    }

    if (service) {
      this.transferSubscription = this.yamcs.yamcsClient.createTransferSubscription({
        instance: this.yamcs.instance!,
        serviceName: service,
      }, transfer => {
        switch (transfer.state) {
          case 'COMPLETED':
            this.transfersById.set(transfer.id, {
              ...transfer,
              objectUrl: this.storageClient.getObjectURL(
                '_global', transfer.bucket, transfer.objectName),
            });
            break;
        }

        const values = [...this.transfersById.values()];
        values.sort((a, b) => a.startTime.localeCompare(b.startTime));
        this.dataSource.data = values;
      });
    }
  }

  ngOnDestroy() {
    if (this.queryParamSubscription) {
      this.queryParamSubscription.unsubscribe();
    }
    if (this.transferSubscription) {
      this.transferSubscription.cancel();
    }
  }
}
