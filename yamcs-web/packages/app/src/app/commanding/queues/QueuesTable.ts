import { AfterViewInit, ChangeDetectionStrategy, Component, Input } from '@angular/core';
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

  constructor(private yamcs: YamcsService) {
  }

  ngAfterViewInit() {
    this.cqueues$.subscribe(cqueues => {
      this.dataSource.data = cqueues;
    });
  }

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
