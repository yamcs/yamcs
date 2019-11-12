import { AfterViewInit, ChangeDetectionStrategy, ChangeDetectorRef, Component, Input } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { CommandQueue } from '@yamcs/client';
import { Observable } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  selector: 'app-queues-table',
  templateUrl: './QueuesTable.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QueuesTable implements AfterViewInit {

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

  constructor(private yamcs: YamcsService, private changeDetection: ChangeDetectorRef) {
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
    this.yamcs.getInstanceClient()!.editCommandQueue(queue.processorName, queue.name, {
      state: 'enabled',
    });
  }

  disableQueue(queue: CommandQueue) {
    this.yamcs.getInstanceClient()!.editCommandQueue(queue.processorName, queue.name, {
      state: 'disabled',
    });
  }

  blockQueue(queue: CommandQueue) {
    this.yamcs.getInstanceClient()!.editCommandQueue(queue.processorName, queue.name, {
      state: 'blocked',
    });
  }
}
