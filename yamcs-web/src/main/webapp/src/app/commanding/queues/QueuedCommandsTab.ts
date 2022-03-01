import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { CommandQueue, QueueEventsSubscription } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './QueuedCommandsTab.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QueuedCommandsTab implements OnDestroy {

  cqueues$ = new BehaviorSubject<CommandQueue[]>([]);

  // True if at least one of the queues has an entry
  hasEntries$ = new BehaviorSubject<boolean>(false);

  private queueEventSubscription: QueueEventsSubscription;

  // Regroup WebSocket updates (which are for 1 queue at a time)
  private cqueueByName: { [key: string]: CommandQueue; } = {};

  constructor(yamcs: YamcsService) {
    yamcs.yamcsClient.getCommandQueues(yamcs.instance!, yamcs.processor!).then(cqueues => {
      for (const cqueue of cqueues) {
        this.cqueueByName[cqueue.name] = cqueue;
      }
      this.emitChange();
    });

    this.queueEventSubscription = yamcs.yamcsClient.createQueueEventsSubscription({
      instance: yamcs.instance!,
      processor: yamcs.processor!,
    }, queueEvent => {
      const queue = this.cqueueByName[queueEvent.data.queueName];
      if (queue) {
        if (queueEvent.type === 'COMMAND_ADDED') {
          queue.entry = queue.entry || [];
          queue.entry.push(queueEvent.data);
        } else if (queueEvent.type === 'COMMAND_UPDATED') {
          const idx = (queue.entry || []).findIndex(entry => {
            return entry.id === queueEvent.data.id;
          });
          if (idx !== -1) {
            queue.entry[idx] = queueEvent.data;
          }
        } else if (queueEvent.type === 'COMMAND_REJECTED') {
          queue.entry = queue.entry || [];
          queue.entry = queue.entry.filter(entry => {
            return entry.id !== queueEvent.data.id;
          });
        } else if (queueEvent.type === 'COMMAND_SENT') {
          queue.entry = queue.entry || [];
          queue.entry = queue.entry.filter(entry => {
            return entry.id !== queueEvent.data.id;
          });
        } else {
          throw new Error(`Unexpected queue event ${queueEvent.type}`);
        }
        this.emitChange();
      } else {
        console.warn('Received an event for an unknown queue', queueEvent);
      }
    });
  }

  private emitChange() {
    this.cqueues$.next(Object.values(this.cqueueByName));

    let hasEntries = false;
    for (const cqueue of this.cqueues$.getValue()) {
      if (cqueue.entry && cqueue.entry.length) {
        hasEntries = true;
        break;
      }
    }
    this.hasEntries$.next(hasEntries);
  }

  ngOnDestroy() {
    if (this.queueEventSubscription) {
      this.queueEventSubscription.cancel();
    }
  }
}
