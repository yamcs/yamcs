import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { MatTableDataSource } from '@angular/material';
import { Transfer } from '@yamcs/client';
import { Subscription } from 'rxjs';
import { CfdpService } from './CfdpService';

@Component({
  templateUrl: './SuccessfulTransfersTab.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SuccessfulTransfersTab implements OnDestroy {

  dataSource = new MatTableDataSource<Transfer>();

  private cfdpSubscription: Subscription;

  constructor(cfdpService: CfdpService) {
    cfdpService.refresh();
    this.cfdpSubscription = cfdpService.transfers$.subscribe(transfers => {
      this.dataSource.data = transfers.filter(
        t => t.state === 'COMPLETED'
      );
    });
  }

  ngOnDestroy() {
    if (this.cfdpSubscription) {
      this.cfdpSubscription.unsubscribe();
    }
  }
}
