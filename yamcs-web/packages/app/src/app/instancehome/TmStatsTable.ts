import { AfterViewInit, ChangeDetectionStrategy, Component, Input, OnDestroy, ViewChild } from '@angular/core';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { Instance, TmStatistics } from '@yamcs/client';
import { Observable, Subscription } from 'rxjs';
import { YamcsService } from '../core/services/YamcsService';

export interface PacketStats {
  packetName: string;
  packetRate: number;
  dataRate: number;
  lastReceived?: string;
  lastPacketTime?: string;
}

@Component({
  selector: 'app-tmstats-table',
  templateUrl: './TmStatsTable.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TmStatsTable implements AfterViewInit, OnDestroy {

  @Input()
  tmstats$: Observable<TmStatistics[]>;
  tmstatsSubscription: Subscription;

  @ViewChild(MatSort, { static: false })
  sort: MatSort;

  private statsByName: { [key: string]: PacketStats } = {};
  dataSource = new MatTableDataSource<PacketStats>();

  instance: Instance;

  displayedColumns = [
    'packetName',
    'lastPacketTime',
    'lastReceived',
    'packetRate',
    'dataRate',
  ];

  constructor(private yamcs: YamcsService) {
    this.instance = yamcs.getInstance();
  }

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
    if (this.tmstats$ && !this.tmstatsSubscription) {
      this.yamcs.getInstanceClient()!.getPacketNames().then(packetNames => {
        for (const packetName of (packetNames || [])) {
          this.statsByName[packetName] = { packetName, packetRate: 0, dataRate: 0 };
        }
        this.dataSource.data = Object.values(this.statsByName);

        this.tmstatsSubscription = this.tmstats$.subscribe(tmstats => {
          for (const entry of (tmstats || [])) {
            this.statsByName[entry.packetName] = entry;
          }
          this.dataSource.data = Object.values(this.statsByName);
        });
      });
    }
  }

  ngOnDestroy() {
    if (this.tmstatsSubscription) {
      this.tmstatsSubscription.unsubscribe();
    }
  }
}
