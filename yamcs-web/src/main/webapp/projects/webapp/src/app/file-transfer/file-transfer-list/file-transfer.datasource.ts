import { CollectionViewer, DataSource } from '@angular/cdk/collections';
import { FileTransferService, GetFileTransfersOptions, StorageClient, Synchronizer, Transfer, TransferSubscription, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { FileTransferBuffer } from './FileTransferBuffer';
import { TransferItem } from './TransferItem';

export class FileTransferDataSource extends DataSource<TransferItem> {

  pageSize = 100;
  options: GetFileTransfersOptions;

  transfers$ = new BehaviorSubject<TransferItem[]>([]);
  private buffer: FileTransferBuffer;

  public loading$ = new BehaviorSubject<boolean>(false);
  public streaming$ = new BehaviorSubject<boolean>(false);

  private realtimeSubscription: TransferSubscription;
  private syncSubscription: Subscription;

  constructor(
    private yamcs: YamcsService,
    synchronizer: Synchronizer,
    private storageClient: StorageClient,
  ) {
    super();
    this.syncSubscription = synchronizer.sync(() => {
      if (this.buffer.dirty && !this.loading$.getValue()) {
        this.emitTransfers();
        this.buffer.dirty = false;
      }
    });

    this.buffer = new FileTransferBuffer(() => {
      this.buffer.compact(500);
    });
  }

  override connect(collectionViewer: CollectionViewer): Observable<readonly TransferItem[]> {
    return this.transfers$;
  }

  private emitTransfers() {
    const transfers = this.buffer.snapshot();
    this.transfers$.next(transfers);
  }

  loadTransfers(service: FileTransferService, options: GetFileTransfersOptions) {
    this.loading$.next(true);
    return Promise.all([
      this.loadPage(service, {
        ...options,
        limit: this.pageSize,
      }),
    ]).then(results => {
      const transfers = results[0].map(transfer => this.toItem(transfer));

      this.loading$.next(false);
      this.buffer.reset();
      this.buffer.addArchiveData(transfers);

      // Quick emit, don't wait on sync tick
      this.emitTransfers();

      return transfers;
    });
  }

  private loadPage(service: FileTransferService, options: GetFileTransfersOptions) {
    this.options = options;
    return this.yamcs.yamcsClient.getFileTransfers(this.yamcs.instance!, service.name, options).then(page => {
      return page.transfers || [];
    });
  }

  startStreaming(service: FileTransferService) {
    this.streaming$.next(true);
    this.realtimeSubscription = this.yamcs.yamcsClient.createTransferSubscription({
      instance: this.yamcs.instance!,
      serviceName: service.name,
      ongoingOnly: true,
    }, transfer => {
      if (!this.loading$.getValue() && this.matchesFilter(transfer)) {
        this.buffer.addRealtimeTransfer(this.toItem(transfer));
      }
    });
  }

  private toItem(transfer: Transfer): TransferItem {
    const objectUrl = transfer.objectName
      ? this.storageClient.getObjectURL(transfer.bucket, transfer.objectName)
      : '';
    return new TransferItem(transfer, objectUrl);
  }

  private matchesFilter(item: Transfer) {
    if (this.options) {
      if (this.options.direction) {
        if (item.direction !== this.options.direction) {
          return false;
        }
      }
      if (this.options.localEntityId) {
        if (item.localEntity.id !== this.options.localEntityId) {
          return false;
        }
      }
      if (this.options.remoteEntityId) {
        if (item.remoteEntity.id !== this.options.remoteEntityId) {
          return false;
        }
      }
      if (this.options.state?.length) {
        if (this.options.state.indexOf(item.state) === -1) {
          return false;
        }
      }
    }

    return true;
  }

  stopStreaming() {
    this.realtimeSubscription?.cancel();
    this.streaming$.next(false);
  }

  override disconnect(collectionViewer: CollectionViewer): void {
    this.stopStreaming();
    this.syncSubscription?.unsubscribe();
    this.transfers$.complete();
    this.loading$.complete();
    this.streaming$.complete();
  }
}
