import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { MatTableDataSource } from '@angular/material';
import { Transfer } from '@yamcs/client';
import { Subscription } from 'rxjs';
import { CfdpService } from './CfdpService';

@Component({
  templateUrl: './OngoingTransfersTab.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OngoingTransfersTab implements OnDestroy {

  dataSource = new MatTableDataSource<Transfer>();

  private cfdpSubscription: Subscription;

  constructor(cfdpService: CfdpService) {
    cfdpService.refresh();
    this.cfdpSubscription = cfdpService.transfers$.subscribe(transfers => {
      this.dataSource.data = transfers.filter(
        t => t.state === 'RUNNING' || t.state === 'PAUSED'
      );
    });
  }

  ngOnDestroy() {
    if (this.cfdpSubscription) {
      this.cfdpSubscription.unsubscribe();
    }
  }
}
