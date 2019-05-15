import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { MatDialog } from '@angular/material';
import { Title } from '@angular/platform-browser';
import { Instance } from '@yamcs/client';
import { BehaviorSubject, Subscription } from 'rxjs';
import { Synchronizer } from '../core/services/Synchronizer';
import { YamcsService } from '../core/services/YamcsService';
import { UploadFileDialog } from './UploadFileDialog';

@Component({
  templateUrl: './FileTransferPage.html',
  styleUrls: ['./FileTransferPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FileTransferPage implements OnDestroy {

  instance: Instance;

  ongoingCount$ = new BehaviorSubject<number>(0);
  failedCount$ = new BehaviorSubject<number>(0);
  successfulCount$ = new BehaviorSubject<number>(0);

  private syncSubscription: Subscription;

  constructor(
    private yamcs: YamcsService,
    title: Title,
    private dialog: MatDialog,
    synchronizer: Synchronizer,
  ) {
    title.setTitle('CFDP File Transfer');
    this.instance = yamcs.getInstance();
    this.refresh();
    this.syncSubscription = synchronizer.sync(() => this.refresh());
  }

  private refresh() {
    this.yamcs.getInstanceClient()!.getCfdpTransfers().then(page => {
      let ongoingCount = 0;
      let failedCount = 0;
      let successfulCount = 0;
      for (const transfer of (page.transfers || [])) {
        if (transfer.state === 'RUNNING' || transfer.state === 'PAUSED') {
          ongoingCount++;
        } else if (transfer.state === 'FAILED') {
          failedCount++;
        } else if (transfer.state === 'COMPLETED') {
          successfulCount++;
        }
      }
      this.ongoingCount$.next(ongoingCount);
      this.failedCount$.next(failedCount);
      this.successfulCount$.next(successfulCount);
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
    if (this.syncSubscription) {
      this.syncSubscription.unsubscribe();
    }
  }
}
