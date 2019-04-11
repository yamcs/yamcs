import { AfterViewInit, ChangeDetectionStrategy, Component, Input, OnDestroy, ViewChild } from '@angular/core';
import { MatSort, MatTableDataSource } from '@angular/material';
import { Instance, TmStatistics } from '@yamcs/client';
import { Observable, Subscription } from 'rxjs';
import { YamcsService } from '../core/services/YamcsService';

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

  instance: Instance;

  displayedColumns = [
    'packetName',
    'receivedPackets',
    'lastPacketTime',
    'lastReceived',
    // 'subscribedParameterCount',
  ];

  constructor(yamcs: YamcsService) {
    this.instance = yamcs.getInstance();
  }

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
