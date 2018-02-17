import { Component, ChangeDetectionStrategy, Input, AfterViewInit, ViewChild } from '@angular/core';
import { Observable } from 'rxjs/Observable';
import { CommandQueueInfo, CommandQueueEntry } from '../../../yamcs-client';
import { MatSort, MatTableDataSource } from '@angular/material';

@Component({
  selector: 'app-queued-commands-table',
  templateUrl: './QueuedCommandsTable.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QueuedCommandsTable implements AfterViewInit {

  @Input()
  info$: Observable<CommandQueueInfo>;

  @ViewChild(MatSort)
  sort: MatSort;

  dataSource = new MatTableDataSource<CommandQueueEntry>();
  private infoByName: { [key: string]: CommandQueueInfo } = {};

  displayedColumns = [
    'generationTime',
    'source',
    'cmdId.origin',
    'queueName',
    'username',
  ];

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
    this.info$.subscribe(info => {
      this.infoByName[info.name] = info;
      console.log('add', info);
      this.refreshDataSource();
    });
  }

  // We receive entries grouped by queue, but rather
  // have a flat list
  private refreshDataSource() {
    const entries = [];
    for (const info of Object.values(this.infoByName)) {
      if (info.entry) {
        entries.push(...info.entry);
      }
    }
    this.dataSource.data = entries;
  }
}
