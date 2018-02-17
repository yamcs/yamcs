import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Observable } from 'rxjs/Observable';

import { CommandQueueInfo } from '../../../yamcs-client';

import { ActivatedRoute } from '@angular/router';

import { YamcsService } from '../../core/services/YamcsService';
import { filter } from 'rxjs/operators';

@Component({
  templateUrl: './ProcessorTCTab.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProcessorTCTab {

  info$: Observable<CommandQueueInfo>;

  constructor(route: ActivatedRoute, yamcs: YamcsService) {
    const parent = route.snapshot.parent!;
    const name = parent.paramMap.get('name')!;
    this.info$ = yamcs.getSelectedInstance().getCommandQueueInfoUpdates().pipe(
      filter(info => info.processorName === name),
    );
  }
}
