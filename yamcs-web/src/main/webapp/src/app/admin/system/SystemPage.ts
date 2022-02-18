import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { BehaviorSubject, Subscription } from 'rxjs';
import { PluginInfo, SystemInfo } from '../../client';
import { Synchronizer } from '../../core/services/Synchronizer';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './SystemPage.html',
  styleUrls: ['./SystemPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SystemPage implements OnDestroy {

  info$ = new BehaviorSubject<SystemInfo | null>(null);
  plugins$ = new BehaviorSubject<PluginInfo[]>([]);

  private syncSubscription: Subscription;

  constructor(
    private yamcs: YamcsService,
    title: Title,
    synchronizer: Synchronizer,
  ) {
    title.setTitle('System');

    this.refresh();
    this.syncSubscription = synchronizer.syncSlow(() => this.refresh());
  }

  private refresh() {
    this.yamcs.yamcsClient.getGeneralInfo().then(info => this.plugins$.next(info.plugins || []));
    this.yamcs.yamcsClient.getSystemInfo().then(info => this.info$.next(info));
  }

  ngOnDestroy() {
    if (this.syncSubscription) {
      this.syncSubscription.unsubscribe();
    }
  }
}
