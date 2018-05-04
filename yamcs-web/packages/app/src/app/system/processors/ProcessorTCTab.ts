import { Component, ChangeDetectionStrategy, OnDestroy } from '@angular/core';

import { CommandQueue } from '@yamcs/client';

import { ActivatedRoute } from '@angular/router';

import { YamcsService } from '../../core/services/YamcsService';
import { BehaviorSubject ,  Subscription } from 'rxjs';

@Component({
  templateUrl: './ProcessorTCTab.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProcessorTCTab implements OnDestroy {

  cqueues$ = new BehaviorSubject<CommandQueue[]>([]);
  cqueueSubscription: Subscription;

  // Regroup WebSocket updates (which are for 1 queue at a time)
  private cqueueByName: { [key: string]: CommandQueue } = {};

  constructor(route: ActivatedRoute, yamcs: YamcsService) {
    const parent = route.snapshot.parent!;
    const processorName = parent.paramMap.get('name')!;
    const instanceClient = yamcs.getInstanceClient()!;

    // Single shot (websocket also provides an initial update,
    // but only first time we navigate to this page)
    instanceClient.getCommandQueues(processorName).then(cqueues => {
      for (const cqueue of cqueues) {
        this.cqueueByName[cqueue.name] = cqueue;
      }
      this.emitChange();
    });

    instanceClient.getCommandQueueUpdates(processorName).then(response => {
      this.cqueueSubscription = response.commandQueue$.subscribe(cqueue => {
        this.cqueueByName[cqueue.name] = cqueue;
        this.emitChange();
      });
    });
  }

  /**
   * Returns true if at least one of the queues has an entry
   */
  hasEntries() {
    for (const cqueue of this.cqueues$.getValue()) {
      if (cqueue.entry && cqueue.entry.length) {
        return true;
      }
    }
    return false;
  }

  private emitChange() {
    this.cqueues$.next(Object.values(this.cqueueByName));
  }

  ngOnDestroy() {
    if (this.cqueueSubscription) {
      this.cqueueSubscription.unsubscribe();
    }
  }
}
