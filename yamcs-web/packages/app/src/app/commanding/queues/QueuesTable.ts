import { AfterViewInit, ChangeDetectionStrategy, Component, Input, ViewChild } from '@angular/core';
import { MatSort } from '@angular/material/sort';
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

  @ViewChild(MatSort, { static: false })
  sort: MatSort;

  dataSource = new MatTableDataSource<CommandQueue>();

  displayedColumns = [
    'name',
    'mode',
    'pending',
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
