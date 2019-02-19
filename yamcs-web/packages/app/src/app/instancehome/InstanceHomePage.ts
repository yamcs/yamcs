import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { GeneralInfo, TmStatistics } from '@yamcs/client';
import { BehaviorSubject, Subscription } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { YamcsService } from '../core/services/YamcsService';

@Component({
  templateUrl: './InstanceHomePage.html',
  styleUrls: ['./InstanceHomePage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InstanceHomePage implements OnDestroy {

  tmstats$ = new BehaviorSubject<TmStatistics[]>([]);
  tmstatsSubscription: Subscription;

  info$: Promise<GeneralInfo>;

  constructor(yamcs: YamcsService) {
    const processor = yamcs.getProcessor();
    yamcs.getInstanceClient()!.getProcessorStatistics().then(response => {
      response.statistics$.pipe(
        filter(stats => stats.yProcessorName === processor.name),
        map(stats => stats.tmstats || []),
      ).subscribe(tmstats => {
        this.tmstats$.next(tmstats);
      });
    });

    this.info$ = yamcs.yamcsClient.getGeneralInfo();
  }

  ngOnDestroy() {
    if (this.tmstatsSubscription) {
      this.tmstatsSubscription.unsubscribe();
    }
  }
}
