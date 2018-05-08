import { Component, ChangeDetectionStrategy, Input, AfterViewInit, ViewChild, OnDestroy } from '@angular/core';
import { Observable ,  Subscription } from 'rxjs';
import { TmStatistics } from '@yamcs/client';
import { MatSort, MatTableDataSource } from '@angular/material';

@Component({
  selector: 'app-tmstats-table',
  templateUrl: './TmStatsTable.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TmStatsTable implements AfterViewInit, OnDestroy {

  @Input()
  tmstats$: Observable<TmStatistics[]>;
  tmstatsSubscription: Subscription;

  @ViewChild(MatSort)
  sort: MatSort;

  dataSource = new MatTableDataSource<TmStatistics>();

  displayedColumns = [
    'packetName',
    'receivedPackets',
    'lastReceivedUTC',
    'lastPacketTimeUTC',
    'subscribedParameterCount',
  ];

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
    if (this.tmstats$ && !this.tmstatsSubscription) {
      this.tmstatsSubscription = this.tmstats$.subscribe(tmstats => {
        this.dataSource.data = tmstats || [];
      });
    }
  }

  ngOnDestroy() {
    if (this.tmstatsSubscription) {
      this.tmstatsSubscription.unsubscribe();
    }
  }
}
