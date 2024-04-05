import { AfterViewInit, ChangeDetectionStrategy, Component, Input, ViewChild } from '@angular/core';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { CommandQueue, CommandQueueEntry, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { Observable } from 'rxjs';

@Component({
  standalone: true,
  selector: 'app-queued-commands-table',
  templateUrl: './queued-commands-table.component.html',
  styleUrl: './queued-commands-table.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class QueuedCommandsTableComponent implements AfterViewInit {

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
        if (cqueue.entries) {
          entries.push(...cqueue.entries);
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
