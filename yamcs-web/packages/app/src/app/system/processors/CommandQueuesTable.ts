import { Component, ChangeDetectionStrategy, Input, AfterViewInit } from '@angular/core';
import { Observable } from 'rxjs/Observable';
import { CommandQueue } from '@yamcs/client';
import { MatTableDataSource } from '@angular/material';

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
    'name',
    'state',
  ];

  ngAfterViewInit() {
    this.cqueues$.subscribe(cqueues => {
      this.dataSource.data = cqueues;
    });
  }
}
