import { AfterViewInit, ChangeDetectionStrategy, Component, Input, ViewChild } from '@angular/core';
import { MatLegacyTableDataSource } from '@angular/material/legacy-table';
import { MatSort } from '@angular/material/sort';
import { Observable } from 'rxjs';
import { CommandQueue, CommandQueueEntry } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  selector: 'app-queued-commands-table',
  templateUrl: './QueuedCommandsTable.html',
  styleUrls: ['./QueuedCommandsTable.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QueuedCommandsTable implements AfterViewInit {

  @Input()
  cqueues$: Observable<CommandQueue[]>;

  @ViewChild(MatSort)
  sort: MatSort;

  dataSource = new MatLegacyTableDataSource<CommandQueueEntry>();

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

  acceptEntry(entry: CommandQueueEntry) {
    if (confirm(`Are you sure you want to accept this command?\n\n ${entry.commandName}`)) {
      this.yamcs.yamcsClient.acceptCommand(entry.instance, entry.processorName, entry.queueName, entry.id);
    }
  }

  rejectEntry(entry: CommandQueueEntry) {
    if (confirm(`Are you sure you want to reject this command?\n\n ${entry.commandName}`)) {
      this.yamcs.yamcsClient.rejectCommand(entry.instance, entry.processorName, entry.queueName, entry.id);
    }
  }
}
