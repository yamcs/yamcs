import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { BehaviorSubject, Observable } from 'rxjs';
import { CommandQueue, ConnectionInfo, QueueEventsSubscription, QueueStatisticsSubscription } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './QueuesPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QueuesPage implements OnDestroy {

  connectionInfo$: Observable<ConnectionInfo | null>;

  cqueues$ = new BehaviorSubject<CommandQueue[]>([]);

  private queueSubscription: QueueStatisticsSubscription;
  private queueEventSubscription: QueueEventsSubscription;

  // Regroup WebSocket updates (which are for 1 queue at a time)
  private cqueueByName: { [key: string]: CommandQueue; } = {};

  constructor(yamcs: YamcsService, title: Title) {
    const processor = yamcs.getProcessor();
    title.setTitle('Queues');
    this.connectionInfo$ = yamcs.connectionInfo$;

    yamcs.yamcsClient.getCommandQueues(yamcs.getInstance(), processor.name).then(cqueues => {
      for (const cqueue of cqueues) {
        this.cqueueByName[cqueue.name] = cqueue;
      }
      this.emitChange();
    });

    this.queueSubscription = yamcs.yamcsClient.createQueueStatisticsSubscription({
      instance: yamcs.getInstance(),
      processor: processor.name,
    }, queue => {
      const existingQueue = this.cqueueByName[queue.name];
      if (existingQueue) {
        // Update queue (but keep already known entries)
        queue.entry = existingQueue.entry;
        this.cqueueByName[queue.name] = queue;
        this.emitChange();
      }
    });

    this.queueEventSubscription = yamcs.yamcsClient.createQueueEventsSubscription({
      instance: yamcs.getInstance(),
      processor: processor.name,
    }, queueEvent => {
      const queue = this.cqueueByName[queueEvent.data.queueName];
      if (queue) {
        if (queueEvent.type === 'COMMAND_ADDED') {
          queue.entry = queue.entry || [];
          queue.entry.push(queueEvent.data);
        } else if (queueEvent.type === 'COMMAND_UPDATED') {
          const idx = (queue.entry || []).findIndex(entry => {
            return entry.uuid === queueEvent.data.uuid;
          });
          if (idx !== -1) {
            queue.entry[idx] = queueEvent.data;
          }
        } else if (queueEvent.type === 'COMMAND_REJECTED') {
          queue.entry = queue.entry || [];
          queue.entry = queue.entry.filter(entry => {
            return entry.uuid !== queueEvent.data.uuid;
          });
        } else if (queueEvent.type === 'COMMAND_SENT') {
          queue.entry = queue.entry || [];
          queue.entry = queue.entry.filter(entry => {
            return entry.uuid !== queueEvent.data.uuid;
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
    if (this.queueSubscription) {
      this.queueSubscription.cancel();
    }
    if (this.queueEventSubscription) {
      this.queueEventSubscription.cancel();
    }
  }
}
