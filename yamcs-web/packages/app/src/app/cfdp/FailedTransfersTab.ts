import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { MatTableDataSource } from '@angular/material';
import { Subscription } from 'rxjs';
import { CfdpService } from './CfdpService';
import { TransferItem } from './TransferItem';

@Component({
  templateUrl: './FailedTransfersTab.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FailedTransfersTab implements OnDestroy {

  dataSource = new MatTableDataSource<TransferItem>();

  private cfdpSubscription: Subscription;

  constructor(cfdpService: CfdpService) {
    cfdpService.refresh();
    this.cfdpSubscription = cfdpService.transfers$.subscribe(transfers => {
      this.dataSource.data = transfers.filter(
        t => t.state === 'FAILED'
      );
    });
  }

  ngOnDestroy() {
    if (this.cfdpSubscription) {
      this.cfdpSubscription.unsubscribe();
    }
  }
}
