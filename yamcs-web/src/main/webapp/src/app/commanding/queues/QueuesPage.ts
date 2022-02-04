import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { BehaviorSubject, Observable } from 'rxjs';
import { CommandQueue, ConnectionInfo, QueueStatisticsSubscription } from '../../client';
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
  }

  private emitChange() {
    this.cqueues$.next(Object.values(this.cqueueByName));
  }

  ngOnDestroy() {
    if (this.queueSubscription) {
      this.queueSubscription.cancel();
    }
  }
}
