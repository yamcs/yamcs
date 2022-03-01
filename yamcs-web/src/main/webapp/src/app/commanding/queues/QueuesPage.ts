import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { CommandQueue, ConnectionInfo, QueueEventsSubscription, QueueStatisticsSubscription } from '../../client';
import { MessageService } from '../../core/services/MessageService';
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

  constructor(readonly yamcs: YamcsService, title: Title, messageService: MessageService) {
    title.setTitle('Queues');
    this.connectionInfo$ = yamcs.connectionInfo$;

    yamcs.yamcsClient.getCommandQueues(yamcs.instance!, yamcs.processor!).then(cqueues => {
      for (const cqueue of cqueues) {
        this.cqueueByName[cqueue.name] = cqueue;
      }
      this.emitChange();
    }).catch(err => messageService.showError(err));

    this.queueSubscription = yamcs.yamcsClient.createQueueStatisticsSubscription({
      instance: yamcs.instance!,
      processor: yamcs.processor!,
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
  }

  ngOnDestroy() {
    this.queueSubscription?.cancel();
    this.queueEventSubscription?.cancel();
  }
}
