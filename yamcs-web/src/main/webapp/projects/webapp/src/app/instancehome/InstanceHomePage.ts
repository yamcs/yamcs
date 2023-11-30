import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ConfigService, TMStatisticsSubscription, TmStatistics, User, WebsiteConfig, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from '../core/services/AuthService';

@Component({
  templateUrl: './InstanceHomePage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InstanceHomePage implements OnDestroy {

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
      this.tmstatsSubscription = yamcs.yamcsClient.createTMStatisticsSubscription({
        instance: this.yamcs.instance!,
        processor: this.yamcs.processor,
      }, stats => this.tmstats$.next(stats.tmstats || []));
    }
  }

  showPackets() {
    return this.user.hasAnyObjectPrivilegeOfType('ReadPacket');
  }

  ngOnDestroy() {
    if (this.tmstatsSubscription) {
      this.tmstatsSubscription.cancel();
    }
  }
}
