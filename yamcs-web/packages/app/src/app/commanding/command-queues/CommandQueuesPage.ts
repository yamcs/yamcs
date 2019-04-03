import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { CommandQueue } from '@yamcs/client';
import { BehaviorSubject, Subscription } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './CommandQueuesPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandQueuesPage implements OnDestroy {

  cqueues$ = new BehaviorSubject<CommandQueue[]>([]);
  cqueueSubscription: Subscription;
  cqueueEventSubscription: Subscription;

  // Regroup WebSocket updates (which are for 1 queue at a time)
  private cqueueByName: { [key: string]: CommandQueue } = {};

  constructor(yamcs: YamcsService) {
    const processor = yamcs.getProcessor();
    const instanceClient = yamcs.getInstanceClient()!;

    instanceClient.getCommandQueues(processor.name).then(cqueues => {
      for (const cqueue of cqueues) {
        this.cqueueByName[cqueue.name] = cqueue;
      }
      this.emitChange();
    });

    instanceClient.getCommandQueueUpdates(processor.name).then(response => {
      this.cqueueSubscription = response.commandQueue$.subscribe(cqueue => {
        const existingQueue = this.cqueueByName[cqueue.name];
        if (existingQueue) {
          // Update queue (but keep already known entries)
          existingQueue.state = cqueue.state;
          // Change object id for change detection
          this.cqueueByName[cqueue.name] = { ...existingQueue };
          this.emitChange();
        }
      });
    });

    instanceClient.getCommandQueueEventUpdates(processor.name).then(response => {
      this.cqueueEventSubscription = response.commandQueueEvent$.subscribe(cqueueEvent => {
        const queue = this.cqueueByName[cqueueEvent.data.queueName];
        if (queue) {
          if (cqueueEvent.type === 'COMMAND_ADDED') {
            queue.entry = queue.entry || [];
            queue.entry.push(cqueueEvent.data);
          } else if (cqueueEvent.type === 'COMMAND_REJECTED') {
            queue.entry = queue.entry || [];
            queue.entry = queue.entry.filter(entry => {
              return entry.uuid !== cqueueEvent.data.uuid;
            });
          } else if (cqueueEvent.type === 'COMMAND_SENT') {
            queue.entry = queue.entry || [];
            queue.entry = queue.entry.filter(entry => {
              return entry.uuid !== cqueueEvent.data.uuid;
            });
          } else {
            throw new Error(`Unexpected queue event ${cqueueEvent.type}`);
          }
          this.emitChange();
        } else {
          console.warn('Received an event for an unknown queue', cqueueEvent);
        }
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
    if (this.cqueueEventSubscription) {
      this.cqueueEventSubscription.unsubscribe();
    }
  }
}
