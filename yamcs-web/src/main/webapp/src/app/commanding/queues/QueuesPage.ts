import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { CommandQueue, ConnectionInfo } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './QueuesPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QueuesPage implements OnDestroy {

  connectionInfo$: Observable<ConnectionInfo | null>;

  cqueues$ = new BehaviorSubject<CommandQueue[]>([]);
  cqueueSubscription: Subscription;
  cqueueEventSubscription: Subscription;

  // Regroup WebSocket updates (which are for 1 queue at a time)
  private cqueueByName: { [key: string]: CommandQueue; } = {};

  constructor(yamcs: YamcsService, title: Title) {
    const processor = yamcs.getProcessor();
    const instanceClient = yamcs.getInstanceClient()!;
    title.setTitle('Queues');
    this.connectionInfo$ = yamcs.connectionInfo$;

    yamcs.yamcsClient.getCommandQueues(processor.instance, processor.name).then(cqueues => {
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
          cqueue.entry = existingQueue.entry;
          this.cqueueByName[cqueue.name] = cqueue;
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
          } else if (cqueueEvent.type === 'COMMAND_UPDATED') {
            const idx = (queue.entry || []).findIndex(entry => {
              return entry.uuid === cqueueEvent.data.uuid;
            });
            if (idx !== -1) {
              queue.entry[idx] = cqueueEvent.data;
            }
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
