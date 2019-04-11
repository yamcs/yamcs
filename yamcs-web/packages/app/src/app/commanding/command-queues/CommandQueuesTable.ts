import { AfterViewInit, ChangeDetectionStrategy, Component, Input, ViewChild } from '@angular/core';
import { MatSort, MatTableDataSource } from '@angular/material';
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

  @ViewChild(MatSort)
  sort: MatSort;

  dataSource = new MatTableDataSource<CommandQueue>();

  displayedColumns = [
    'state',
    'name',
    'labels',
    'actions',
  ];

  constructor(private yamcs: YamcsService) {
  }

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;

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
