import { AfterViewInit, ChangeDetectionStrategy, Component, Input, ViewChild } from '@angular/core';
import { MatSort, MatTableDataSource } from '@angular/material';
import { CommandQueue, CommandQueueEntry } from '@yamcs/client';
import { Observable } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  selector: 'app-queued-commands-table',
  templateUrl: './QueuedCommandsTable.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QueuedCommandsTable implements AfterViewInit {

  @Input()
  cqueues$: Observable<CommandQueue[]>;

  @ViewChild(MatSort)
  sort: MatSort;

  dataSource = new MatTableDataSource<CommandQueueEntry>();

  displayedColumns = [
    'generationTime',
    'source',
    'cmdId.origin',
    'queueName',
    'username',
    'actions',
  ];

  constructor(private yamcs: YamcsService) {
  }

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;

    // We receive entries grouped by queue, but rather
    // have a flat list
    this.cqueues$.subscribe(cqueues => {
      const entries = [];
      for (const cqueue of cqueues) {
        if (cqueue.entry) {
          entries.push(...cqueue.entry);
        }
      }
      this.dataSource.data = entries;
      console.log('entries', entries);
    });
  }

  releaseEntry(entry: CommandQueueEntry) {
    this.yamcs.getInstanceClient()!.editCommandQueueEntry(entry.processorName, entry.queueName, entry.uuid, {
      state: 'released',
    });
  }

  rejectEntry(entry: CommandQueueEntry) {
    this.yamcs.getInstanceClient()!.editCommandQueueEntry(entry.processorName, entry.queueName, entry.uuid, {
      state: 'rejected',
    });
  }
}
