import { ChangeDetectionStrategy, Component, Input, ViewChild } from '@angular/core';
import { TmStatistics, WebappSdkModule } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Observable } from 'rxjs';
import { TmStatsTableComponent } from '../tm-stats-table/tm-stats-table.component';

@Component({
  selector: 'app-packet-overlay',
  templateUrl: './packet-overlay.component.html',
  styleUrl: './packet-overlay.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [WebappSdkModule, TmStatsTableComponent],
})
export class PacketOverlayComponent {
  @Input() tmstats$: Observable<TmStatistics[]>;
  @Input() showPackets: boolean = true;

  @ViewChild(TmStatsTableComponent) tmStatsTable?: TmStatsTableComponent;

  isCollapsed$ = new BehaviorSubject<boolean>(true);

  toggleCollapse() {
    this.isCollapsed$.next(!this.isCollapsed$.value);
  }

  getTotalPacketCount(): number {
    if (!this.tmStatsTable) return 0;
    
    // Sum up packet counts from all packet types
    let totalPackets = 0;
    for (const stats of this.tmStatsTable.dataSource.data) {
      // Cast to TmStatistics to access receivedPackets property
      const tmStats = stats as any;
      if (tmStats.receivedPackets !== undefined) {
        totalPackets += Number(tmStats.receivedPackets);
      } 
      // Do not do estimation.
      // else if (stats.packetRate) {
      //   // Fallback: estimate from rate if receivedPackets not available
      //   totalPackets += Math.floor(stats.packetRate * 60);
      // }
    }
    
    return totalPackets;
  }

  getTotalPacketRate(): number {
    if (!this.tmStatsTable) return 0;
    return this.tmStatsTable.totalPacketRate$.value;
  }
}