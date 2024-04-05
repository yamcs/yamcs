import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { ActivatedRoute } from '@angular/router';
import { StorageClient, Synchronizer, Transfer, TransferSubscription, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';
import { FileTransferTableComponent } from '../file-transfer-table/file-transfer-table.component';
import { TransferItem } from '../shared/TransferItem';

@Component({
  standalone: true,
  selector: 'app-successful-transfers-tab',
  templateUrl: './successful-transfers-tab.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
    FileTransferTableComponent,
  ],
})
export class SuccessfulTransfersTabComponent implements OnDestroy {

  serviceName$ = new BehaviorSubject<string | null>(null);
  dataSource = new MatTableDataSource<TransferItem>();

  private storageClient: StorageClient;
  private transfersById = new Map<number, TransferItem>();
  private transferSubscription: TransferSubscription;

  private dirty = false;
  private syncSubscription: Subscription;

  private queryParamSubscription: Subscription;

  hasTransferType = false;

  constructor(
    private yamcs: YamcsService,
    route: ActivatedRoute,
    synchronizer: Synchronizer,
  ) {
    this.hasTransferType = history.state.hasTransferType;
    this.storageClient = yamcs.createStorageClient();
    this.queryParamSubscription = route.queryParamMap.subscribe(params => {
      const service = params.get('service');
      this.serviceName$.next(service);
      this.switchService(service);
    });
    this.syncSubscription = synchronizer.sync(() => {
      if (this.dirty) {
        const values = [...this.transfersById.values()];
        values.sort((a, b) => this.compareTransfers(a.transfer, b.transfer));
        this.dataSource.data = values;
        this.dirty = false;
      }
    });
  }

  private compareTransfers(a: Transfer, b: Transfer) {
    const time1 = a.creationTime || a.startTime || "";
    const time2 = b.creationTime || b.startTime || "";
    // most recent transfers on top
    return time2.localeCompare(time1);
  }

  private switchService(service: string | null) {
    // Clear state
    this.dirty = false;
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
            this.setOrUpdate(transfer);
            break;
        }

        // Throttle updates, it can get spammy
        this.dirty = true;
      });
    }
  }

  // Do our best to preserve top-level object identity
  // It improves change detection behaviour
  private setOrUpdate(transfer: Transfer) {
    let item = this.transfersById.get(transfer.id);
    if (item) {
      item.updateTransfer(transfer);
    } else {
      const objectUrl = transfer.objectName ? this.storageClient.getObjectURL(transfer.bucket, transfer.objectName) : '';
      item = new TransferItem(transfer, objectUrl);
      this.transfersById.set(transfer.id, item);
    }
  }

  ngOnDestroy() {
    this.syncSubscription?.unsubscribe();
    this.queryParamSubscription?.unsubscribe();
    this.transferSubscription?.cancel();
  }
}
