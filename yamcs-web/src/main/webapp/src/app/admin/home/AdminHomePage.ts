import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { BehaviorSubject, Subscription } from 'rxjs';
import { SystemInfo } from '../../client';
import { Synchronizer } from '../../core/services/Synchronizer';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './AdminHomePage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminHomePage implements OnDestroy {

  info$ = new BehaviorSubject<SystemInfo | null>(null);

  private syncSubscription: Subscription;

  constructor(
    private yamcs: YamcsService,
    title: Title,
    synchronizer: Synchronizer,
  ) {
    title.setTitle('Admin Area');

    this.refresh();
    this.syncSubscription = synchronizer.syncSlow(() => this.refresh());
  }

  private refresh() {
    this.yamcs.yamcsClient.getSystemInfo().then(info => this.info$.next(info));
  }

  ngOnDestroy() {
    if (this.syncSubscription) {
      this.syncSubscription.unsubscribe();
    }
  }
}
