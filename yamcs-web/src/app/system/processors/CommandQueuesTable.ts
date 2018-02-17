import { Component, ChangeDetectionStrategy, Input, AfterViewInit, ViewChild } from '@angular/core';
import { Observable } from 'rxjs/Observable';
import { CommandQueueInfo } from '../../../yamcs-client';
import { MatSort, MatTableDataSource } from '@angular/material';

@Component({
  selector: 'app-command-queues-table',
  templateUrl: './CommandQueuesTable.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandQueuesTable implements AfterViewInit {

  @Input()
  info$: Observable<CommandQueueInfo>;

  dataSource = new MatTableDataSource<CommandQueueInfo>();
  private infoByName: { [key: string]: CommandQueueInfo } = {};

  displayedColumns = [
    'name',
    'state',
  ];

  ngAfterViewInit() {
    this.info$.subscribe(info => {
      this.infoByName[info.name] = info;
      this.dataSource.data = Object.values(this.infoByName);
    });
  }
}
