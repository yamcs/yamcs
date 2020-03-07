import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { publish, refCount } from 'rxjs/operators';
import { StorageClient } from '../client';
import { YamcsService } from '../core/services/YamcsService';
import { TransferItem } from './TransferItem';

// TODO this should actually receive updates over websocket
@Injectable({
  providedIn: 'root',
})
export class CfdpService {

  private subject = new BehaviorSubject<TransferItem[]>([]);
  transfers$: Observable<TransferItem[]>;

  private storageClient: StorageClient;

  constructor(private yamcs: YamcsService) {
    this.storageClient = yamcs.createStorageClient();
    this.transfers$ = this.subject.pipe(
      publish(),
      refCount(),
    );
  }

  public refresh() {
    this.yamcs.yamcsClient.getCfdpTransfers(this.yamcs.getInstance().name).then(page => {
      const items: TransferItem[] = [];
      for (const transfer of (page.transfer || [])) {
        items.push({
          ...transfer,
          objectUrl: this.storageClient.getObjectURL(
            '_global', transfer.bucket, transfer.objectName),
        });
      }

      this.subject.next(items);
    });
  }
}
