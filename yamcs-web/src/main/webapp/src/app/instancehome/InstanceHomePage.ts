import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { BehaviorSubject } from 'rxjs';
import { AlarmsDataSource } from '../alarms/AlarmsDataSource';
import { GeneralInfo, MissionDatabase, TmStatistics, TMStatisticsSubscription } from '../client';
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

  instance: string;

  private user: User;
  config: WebsiteConfig;

  tmstats$ = new BehaviorSubject<TmStatistics[]>([]);
  tmstatsSubscription: TMStatisticsSubscription;

  alarmsDataSource: AlarmsDataSource;

  info$: Promise<GeneralInfo>;
  mdb$: Promise<MissionDatabase>;

  constructor(
    yamcs: YamcsService,
    private authService: AuthService,
    title: Title,
    configService: ConfigService,
  ) {
    this.config = configService.getConfig();

    const processor = yamcs.getProcessor();
    this.instance = yamcs.getInstance();
    this.user = authService.getUser()!;
    title.setTitle(this.instance);
    this.tmstatsSubscription = yamcs.yamcsClient.createTMStatisticsSubscription({
      instance: this.instance,
      processor: processor.name,
    }, stats => this.tmstats$.next(stats.tmstats || []));

    this.alarmsDataSource = new AlarmsDataSource(yamcs);
    this.alarmsDataSource.loadAlarms('realtime');

    if (this.showMDB()) {
      this.mdb$ = yamcs.yamcsClient.getMissionDatabase();
    }

    this.info$ = yamcs.yamcsClient.getGeneralInfo();
  }

  showMDB() {
    return this.user.hasSystemPrivilege('GetMissionDatabase');
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
