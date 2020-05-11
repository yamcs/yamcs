import { AfterViewInit, ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, OnDestroy } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { CommandQueue } from '../../client';
import { Synchronizer } from '../../core/services/Synchronizer';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  selector: 'app-queues-table',
  templateUrl: './QueuesTable.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QueuesTable implements AfterViewInit, OnDestroy {

  @Input()
  cqueues$: Observable<CommandQueue[]>;

  dataSource = new MatTableDataSource<CommandQueue>();

  displayedColumns = [
    'order',
    'name',
    'issuer',
    'level',
    'action',
    'pending',
    'actions',
  ];

  visibility$ = new BehaviorSubject<boolean>(true);
  syncSubscription: Subscription;

  constructor(
    private yamcs: YamcsService,
    private changeDetection: ChangeDetectorRef,
    synchronizer: Synchronizer,
  ) {
    this.syncSubscription = synchronizer.syncFast(() => {
      this.visibility$.next(!this.visibility$.value);
    });
  }

  ngAfterViewInit() {
    this.cqueues$.subscribe(cqueues => {
      this.dataSource.data = cqueues;

      // Needed to show table updates in combination with trackBy
      this.changeDetection.detectChanges();
    });
  }

  // trackBy is needed to prevent menu from closing when the queue object is updated
  tableTrackerFn = (index: number, queue: CommandQueue) => queue.name;

  enableQueue(queue: CommandQueue) {
    const count = queue.entry?.length || 0;
    let msg = 'Are you sure you want to change this queue\'s action to ACCEPT?\n\n';
    if (count === 1) {
      msg += `There is ${count} queued command that will be accepted immediately.`;
    } else {
      msg += `There are ${count} queued commands that will be accepted immediately.`;
    }
    if (count && !confirm(msg)) {
      return;
    }

    this.yamcs.yamcsClient.editCommandQueue(queue.instance, queue.processorName, queue.name, {
      state: 'enabled',
    });
  }

  disableQueue(queue: CommandQueue) {
    const count = queue.entry?.length || 0;
    let msg = 'Are you sure you want to change this queue\'s action to REJECT?\n\n';
    if (count === 1) {
      msg += `There is ${count} queued command that will be rejected immediately.`;
    } else {
      msg += `There are ${count} queued commands that will be rejected immediately.`;
    }
    if (count && !confirm(msg)) {
      return;
    }

    this.yamcs.yamcsClient.editCommandQueue(queue.instance, queue.processorName, queue.name, {
      state: 'disabled',
    });
  }

  blockQueue(queue: CommandQueue) {
    this.yamcs.yamcsClient.editCommandQueue(queue.instance, queue.processorName, queue.name, {
      state: 'blocked',
    });
  }

  ngOnDestroy() {
    if (this.syncSubscription) {
      this.syncSubscription.unsubscribe();
    }
  }
}
