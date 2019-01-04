import { AfterViewInit, ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { MatTableDataSource } from '@angular/material';
import { CommandQueue } from '@yamcs/client';
import { Observable } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  selector: 'app-command-queues-table',
  templateUrl: './CommandQueuesTable.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandQueuesTable implements AfterViewInit {

  @Input()
  cqueues$: Observable<CommandQueue[]>;

  dataSource = new MatTableDataSource<CommandQueue>();

  displayedColumns = [
    'state',
    'name',
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
