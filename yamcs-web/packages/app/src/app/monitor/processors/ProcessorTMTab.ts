import { Component, ChangeDetectionStrategy, OnDestroy } from '@angular/core';

import { TmStatistics } from '@yamcs/client';

import { ActivatedRoute } from '@angular/router';

import { YamcsService } from '../../core/services/YamcsService';
import { filter, map } from 'rxjs/operators';
import { BehaviorSubject ,  Subscription } from 'rxjs';

@Component({
  templateUrl: './ProcessorTMTab.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProcessorTMTab implements OnDestroy {

  tmstats$ = new BehaviorSubject<TmStatistics[]>([]);
  tmstatsSubscription: Subscription;

  constructor(route: ActivatedRoute, yamcs: YamcsService) {
    const parent = route.snapshot.parent!;
    const name = parent.paramMap.get('name')!;

    yamcs.getInstanceClient()!.getProcessorStatistics().then(response => {
      response.statistics$.pipe(
        filter(stats => stats.yProcessorName === name),
        map(stats => stats.tmstats || []),
      ).subscribe(tmstats => {
        this.tmstats$.next(tmstats);
      });
    });
  }

  ngOnDestroy() {
    if (this.tmstatsSubscription) {
      this.tmstatsSubscription.unsubscribe();
    }
  }
}
