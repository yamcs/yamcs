import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Title } from '@angular/platform-browser';
import { BehaviorSubject, Subscription } from 'rxjs';
import { YamcsService } from '../core/services/YamcsService';
import { CfdpService } from './CfdpService';
import { UploadFileDialog } from './UploadFileDialog';

@Component({
  templateUrl: './FileTransferPage.html',
  styleUrls: ['./FileTransferPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FileTransferPage implements OnDestroy {

  instance: string;

  ongoingCount$ = new BehaviorSubject<number>(0);
  failedCount$ = new BehaviorSubject<number>(0);
  successfulCount$ = new BehaviorSubject<number>(0);

  private cfdpSubscription: Subscription;

  constructor(
    yamcs: YamcsService,
    title: Title,
    private dialog: MatDialog,
    private cfdpService: CfdpService,
  ) {
    title.setTitle('CFDP File Transfer');
    this.instance = yamcs.getInstance();
    this.cfdpSubscription = cfdpService.transfers$.subscribe(transfers => {
      let ongoingCount = 0;
      let failedCount = 0;
      let successfulCount = 0;
      for (const transfer of transfers) {
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

  public refresh() {
    this.cfdpService.refresh();
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
    if (this.cfdpSubscription) {
      this.cfdpSubscription.unsubscribe();
    }
  }
}
