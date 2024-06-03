import { AfterViewInit, ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { CommandQueue, ConnectionInfo, MessageService, QueueEventsSubscription, QueueStatisticsSubscription, Synchronizer, TrackBySelectionModel, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { InstancePageTemplateComponent } from '../../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../../shared/instance-toolbar/instance-toolbar.component';
import { SignificanceLevelComponent } from '../../../shared/significance-level/significance-level.component';

@Component({
  standalone: true,
  templateUrl: './queues-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    WebappSdkModule,
    SignificanceLevelComponent,
  ],
})
export class QueuesListComponent implements AfterViewInit, OnDestroy {

  connectionInfo$: Observable<ConnectionInfo | null>;

  cqueues$ = new BehaviorSubject<CommandQueue[]>([]);

  dataSource = new MatTableDataSource<CommandQueue>();
  selection = new TrackBySelectionModel<CommandQueue>((index: number, queue: CommandQueue) => {
    return queue.name;
  }, true, []);

  // trackBy is needed to prevent menu from closing when the queue object is updated
  tableTrackerFn = (index: number, queue: CommandQueue) => queue.name;

  displayedColumns = [
    'select',
    'order',
    'name',
    'issuer',
    'level',
    'patterns',
    'action',
    'pending',
    'actions',
  ];

  visibility$ = new BehaviorSubject<boolean>(true);
  syncSubscription: Subscription;

  private queueSubscription: QueueStatisticsSubscription;
  private queueEventSubscription: QueueEventsSubscription;

  // Regroup WebSocket updates (which are for 1 queue at a time)
  private cqueueByName: { [key: string]: CommandQueue; } = {};

  constructor(
    readonly yamcs: YamcsService,
    title: Title,
    messageService: MessageService,
    private changeDetection: ChangeDetectorRef,
    synchronizer: Synchronizer,
  ) {
    title.setTitle('Queues');
    this.connectionInfo$ = yamcs.connectionInfo$;

    yamcs.yamcsClient.getCommandQueues(yamcs.instance!, yamcs.processor!).then(cqueues => {
      for (const cqueue of cqueues) {
        this.cqueueByName[cqueue.name] = cqueue;
      }
      this.emitChange();
    }).catch(err => messageService.showError(err));

    this.syncSubscription = synchronizer.syncFast(() => {
      this.visibility$.next(!this.visibility$.value);
    });

    this.queueSubscription = yamcs.yamcsClient.createQueueStatisticsSubscription({
      instance: yamcs.instance!,
      processor: yamcs.processor!,
    }, queue => {
      const existingQueue = this.cqueueByName[queue.name];
      if (existingQueue) {
        // Update queue (but keep already known entries)
        queue.entries = existingQueue.entries;
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
          queue.entries = queue.entries || [];
          queue.entries.push(queueEvent.data);
        } else if (queueEvent.type === 'COMMAND_UPDATED') {
          const idx = (queue.entries || []).findIndex(entries => {
            return entries.id === queueEvent.data.id;
          });
          if (idx !== -1) {
            queue.entries[idx] = queueEvent.data;
          }
        } else if (queueEvent.type === 'COMMAND_REJECTED') {
          queue.entries = queue.entries || [];
          queue.entries = queue.entries.filter(entry => {
            return entry.id !== queueEvent.data.id;
          });
        } else if (queueEvent.type === 'COMMAND_SENT') {
          queue.entries = queue.entries || [];
          queue.entries = queue.entries.filter(entry => {
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

  ngAfterViewInit() {
    this.cqueues$.subscribe(cqueues => {
      this.dataSource.data = cqueues;
      this.selection.matchNewValues(cqueues);

      // Needed to show table updates in combination with trackBy
      this.changeDetection.detectChanges();
    });
  }

  isAllSelected() {
    const numSelected = this.selection.selected.length;
    const numRows = this.dataSource.filteredData.length;
    return numSelected === numRows && numRows > 0;
  }

  masterToggle() {
    this.isAllSelected() ?
      this.selection.clear() :
      this.dataSource.filteredData.forEach(row => this.selection.select(row));
  }

  toggleOne(row: CommandQueue) {
    if (!this.selection.isSelected(row) || this.selection.selected.length > 1) {
      this.selection.clear();
    }
    this.selection.toggle(row);
  }

  enableSelectedQueues() {
    for (const item of this.selection.selected) {
      this.enableQueue(item);
    }
  }

  enableQueue(queue: CommandQueue) {
    const count = queue.entries?.length || 0;
    let msg = `Are you sure you want to change the '${queue.name}' queue's action to ACCEPT?\n\n`;
    if (count === 1) {
      msg += `There is ${count} queued command that will be accepted immediately.`;
    } else {
      msg += `There are ${count} queued commands that will be accepted immediately.`;
    }
    if (count && !confirm(msg)) {
      return;
    }

    this.yamcs.yamcsClient.enableCommandQueue(queue.instance, queue.processorName, queue.name);
  }

  disableSelectedQueues() {
    for (const item of this.selection.selected) {
      this.disableQueue(item);
    }
  }

  disableQueue(queue: CommandQueue) {
    const count = queue.entries?.length || 0;
    let msg = `Are you sure you want to change the '${queue.name}' queue\'s action to REJECT?\n\n`;
    if (count === 1) {
      msg += `There is ${count} queued command that will be rejected immediately.`;
    } else {
      msg += `There are ${count} queued commands that will be rejected immediately.`;
    }
    if (count && !confirm(msg)) {
      return;
    }

    this.yamcs.yamcsClient.disableCommandQueue(queue.instance, queue.processorName, queue.name);
  }

  blockSelectedQueues() {
    for (const item of this.selection.selected) {
      this.blockQueue(item);
    }
  }

  blockQueue(queue: CommandQueue) {
    this.yamcs.yamcsClient.blockCommandQueue(queue.instance, queue.processorName, queue.name);
  }

  private emitChange() {
    this.cqueues$.next(Object.values(this.cqueueByName));
  }

  ngOnDestroy() {
    this.syncSubscription?.unsubscribe();
    this.queueSubscription?.cancel();
    this.queueEventSubscription?.cancel();
  }
}
