import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ConfigService, TMStatisticsSubscription, TmStatistics, User, WebappSdkModule, WebsiteConfig, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from '../../core/services/AuthService';
import { InstancePageTemplateComponent } from '../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../shared/instance-toolbar/instance-toolbar.component';
import { TmStatsTableComponent } from '../tm-stats-table/tm-stats-table.component';

@Component({
  standalone: true,
  templateUrl: './instance-home.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    WebappSdkModule,
    TmStatsTableComponent,
  ],
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
    this.tmstatsSubscription?.cancel();
  }
}
