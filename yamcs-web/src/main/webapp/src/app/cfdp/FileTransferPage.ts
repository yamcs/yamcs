import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Title } from '@angular/platform-browser';
import { BehaviorSubject } from 'rxjs';
import { Transfer, TransferSubscription } from '../client';
import { YamcsService } from '../core/services/YamcsService';
import { UploadFileDialog } from './UploadFileDialog';

@Component({
  templateUrl: './FileTransferPage.html',
  styleUrls: ['./FileTransferPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FileTransferPage implements OnDestroy {

  private ongoingTransfersById = new Map<number, Transfer>();
  private failedTransfersById = new Map<number, Transfer>();
  private successfulTransfersById = new Map<number, Transfer>();

  ongoingCount$ = new BehaviorSubject<number>(0);
  failedCount$ = new BehaviorSubject<number>(0);
  successfulCount$ = new BehaviorSubject<number>(0);

  private transferSubscription: TransferSubscription;

  constructor(
    readonly yamcs: YamcsService,
    title: Title,
    private dialog: MatDialog,
  ) {
    title.setTitle('CFDP File Transfer');

    this.transferSubscription = yamcs.yamcsClient.createTransferSubscription({ instance: yamcs.instance! }, transfer => {
      switch (transfer.state) {
        case 'RUNNING':
        case 'PAUSED':
          this.ongoingTransfersById.set(transfer.id, transfer);
          break;
        case 'FAILED':
          this.ongoingTransfersById.delete(transfer.id);
          this.failedTransfersById.set(transfer.id, transfer);
          break;
        case 'COMPLETED':
          this.ongoingTransfersById.delete(transfer.id);
          this.successfulTransfersById.set(transfer.id, transfer);
          break;
      }

      this.ongoingCount$.next(this.ongoingTransfersById.size);
      this.failedCount$.next(this.failedTransfersById.size);
      this.successfulCount$.next(this.successfulTransfersById.size);
    });
  }

  uploadFile() {
    const dialogRef = this.dialog.open(UploadFileDialog, {
      width: '70%',
      height: '100%',
      autoFocus: false,
      position: {
        right: '0',
      }
    });
  }

  ngOnDestroy() {
    if (this.transferSubscription) {
      this.transferSubscription.cancel();
    }
  }
}
