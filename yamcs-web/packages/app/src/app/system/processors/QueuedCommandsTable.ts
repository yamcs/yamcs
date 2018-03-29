import { Component, ChangeDetectionStrategy, Input, AfterViewInit, ViewChild } from '@angular/core';
import { Observable } from 'rxjs/Observable';
import { CommandQueue, CommandQueueEntry } from '@yamcs/client';
import { MatSort, MatTableDataSource } from '@angular/material';

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
  ];

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
}
