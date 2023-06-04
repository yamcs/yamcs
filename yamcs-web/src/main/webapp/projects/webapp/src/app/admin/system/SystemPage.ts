import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { BehaviorSubject } from 'rxjs';
import { PluginInfo, SystemInfo, SystemInfoSubscription } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './SystemPage.html',
  styleUrls: ['./SystemPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SystemPage implements OnDestroy {

  info$ = new BehaviorSubject<SystemInfo | null>(null);
  plugins$ = new BehaviorSubject<PluginInfo[]>([]);

  private systemInfoSubscription: SystemInfoSubscription;

  constructor(
    private yamcs: YamcsService,
    title: Title,
  ) {
    title.setTitle('System');
    this.yamcs.yamcsClient.getGeneralInfo().then(info => this.plugins$.next(info.plugins || []));
    this.systemInfoSubscription = yamcs.yamcsClient.createSystemInfoSubscription(info => {
      this.info$.next(info);
    });
  }

  ngOnDestroy() {
    this.systemInfoSubscription?.cancel();
  }
}
