import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Observable } from 'rxjs/Observable';

import { TmStatistics } from '@yamcs/client';

import { ActivatedRoute } from '@angular/router';

import { YamcsService } from '../../core/services/YamcsService';
import { filter, map } from 'rxjs/operators';

@Component({
  templateUrl: './ProcessorTMTab.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProcessorTMTab {

  tmstats$: Observable<TmStatistics[]>;

  constructor(route: ActivatedRoute, yamcs: YamcsService) {
    const parent = route.snapshot.parent!;
    const name = parent.paramMap.get('name')!;

    this.tmstats$ = yamcs.getSelectedInstance().getProcessorStatistics().pipe(
      filter(stats => stats.yProcessorName === name),
      map(stats => stats.tmstats || []),
    );
  }
}
