import { Component, ChangeDetectionStrategy, Input, AfterViewInit, ViewChild } from '@angular/core';
import { Observable } from 'rxjs/Observable';
import { TmStatistics } from '../../../yamcs-client';
import { MatSort, MatTableDataSource } from '@angular/material';

@Component({
  selector: 'app-tmstats-table',
  templateUrl: './TmStatsTable.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TmStatsTable implements AfterViewInit {

  @Input()
  tmstats$: Observable<TmStatistics[]>;

  @ViewChild(MatSort)
  sort: MatSort;

  dataSource = new MatTableDataSource<TmStatistics>();

  displayedColumns = ['packetName', 'receivedPackets', 'lastReceivedUTC', 'lastPacketTimeUTC', 'subscribedParameterCount'];

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
    this.tmstats$.subscribe(tmstats => {
      this.dataSource.data = tmstats || [];
    });
  }
}
