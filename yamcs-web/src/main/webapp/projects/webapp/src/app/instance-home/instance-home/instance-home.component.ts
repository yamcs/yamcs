import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { Title } from '@angular/platform-browser';
import {
  AuthService,
  ConfigService,
  TMStatisticsSubscription,
  TmStatistics,
  User,
  WebappSdkModule,
  WebsiteConfig,
  YamcsService,
} from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { SatelliteMapComponent } from '../satellite-map/satellite-map.component';
import { PacketOverlayComponent } from '../packet-overlay/packet-overlay.component';

@Component({
  templateUrl: './instance-home.component.html',
  styleUrl: './instance-home.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [WebappSdkModule, SatelliteMapComponent, PacketOverlayComponent],
})
export class InstanceHomeComponent implements OnDestroy {
  private user: User;
  config: WebsiteConfig;

  tmstats$ = new BehaviorSubject<TmStatistics[]>([]);
  tmstatsSubscription: TMStatisticsSubscription;

  constructor(
    readonly yamcs: YamcsService,
    private authService: AuthService,
    title: Title,
    configService: ConfigService,
  ) {
    this.config = configService.getConfig();

    this.user = authService.getUser()!;
    title.setTitle(this.yamcs.instance!);
    if (this.yamcs.processor) {
      this.tmstatsSubscription =
        yamcs.yamcsClient.createTMStatisticsSubscription(
          {
            instance: this.yamcs.instance!,
            processor: this.yamcs.processor,
          },
          (stats) => this.tmstats$.next(stats.tmstats || []),
        );
    }
  }

  showPackets() {
    return this.user.hasAnyObjectPrivilegeOfType('ReadPacket');
  }

  ngOnDestroy() {
    this.tmstatsSubscription?.cancel();
  }
}
