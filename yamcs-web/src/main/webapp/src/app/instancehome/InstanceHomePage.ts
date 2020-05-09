import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { BehaviorSubject } from 'rxjs';
import { AlarmsDataSource } from '../alarms/AlarmsDataSource';
import { TmStatistics, TMStatisticsSubscription } from '../client';
import { AuthService } from '../core/services/AuthService';
import { ConfigService, WebsiteConfig } from '../core/services/ConfigService';
import { YamcsService } from '../core/services/YamcsService';
import { User } from '../shared/User';

@Component({
  templateUrl: './InstanceHomePage.html',
  styleUrls: ['./InstanceHomePage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InstanceHomePage implements OnDestroy {

  private user: User;
  config: WebsiteConfig;

  tmstats$ = new BehaviorSubject<TmStatistics[]>([]);
  tmstatsSubscription: TMStatisticsSubscription;

  alarmsDataSource: AlarmsDataSource;

  constructor(
    readonly yamcs: YamcsService,
    private authService: AuthService,
    title: Title,
    configService: ConfigService,
  ) {
    this.config = configService.getConfig();

    this.user = authService.getUser()!;
    title.setTitle(this.yamcs.instance!);
    this.tmstatsSubscription = yamcs.yamcsClient.createTMStatisticsSubscription({
      instance: this.yamcs.instance!,
      processor: this.yamcs.processor!,
    }, stats => this.tmstats$.next(stats.tmstats || []));

    this.alarmsDataSource = new AlarmsDataSource(yamcs);
    this.alarmsDataSource.loadAlarms();
  }

  showAlarms() {
    return this.user.hasSystemPrivilege('ReadAlarms');
  }

  showPackets() {
    return this.user.hasAnyObjectPrivilegeOfType('ReadPacket');
  }

  ngOnDestroy() {
    if (this.tmstatsSubscription) {
      this.tmstatsSubscription.cancel();
    }
    if (this.alarmsDataSource) {
      this.alarmsDataSource.disconnect();
    }
  }
}
