import { AfterViewInit, ChangeDetectionStrategy, Component, Input, ViewChild } from '@angular/core';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { Observable } from 'rxjs';
import { CommandQueue, CommandQueueEntry } from '../../client';
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
    'comment',
    'source',
    'queueName',
    'issuer',
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
    });
  }

  releaseEntry(entry: CommandQueueEntry) {
    if (confirm(`Are you sure you want to accept this command?\n\n ${entry.source}`)) {
      this.yamcs.yamcsClient.editCommandQueueEntry(entry.instance, entry.processorName, entry.queueName, entry.uuid, {
        state: 'released',
      });
    }
  }

  rejectEntry(entry: CommandQueueEntry) {
    if (confirm(`Are you sure you want to reject this command?\n\n ${entry.source}`)) {
      this.yamcs.yamcsClient.editCommandQueueEntry(entry.instance, entry.processorName, entry.queueName, entry.uuid, {
        state: 'rejected',
      });
    }
  }
}
