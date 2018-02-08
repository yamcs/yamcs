import { ChangeDetectionStrategy, Component, Input, OnInit, ViewChild } from '@angular/core';
import { DataSource } from '@angular/cdk/table';
import { MatPaginator } from '@angular/material';

import { map } from 'rxjs/operators';
import { merge } from 'rxjs/observable/merge';
import { of } from 'rxjs/observable/of';
import { Event } from '../../../yamcs-client';

@Component({
  selector: 'app-event-table',
  templateUrl: './event-table.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EventTableComponent implements OnInit {

  @Input() events: Event[];
  @Input() pageIndex = 0;
  @Input() pageSize = 25;

  @ViewChild(MatPaginator) paginator: MatPaginator;

  displayedColumns = ['name', 'elapsed'];
  dataSource: EventDataSource | null;

  ngOnInit() {
    this.dataSource = new EventDataSource(this.events, this.paginator);
  }
}

class EventDataSource extends DataSource<Event> {

  constructor(private events: Event[], private paginator: MatPaginator) {
    super();
  }

  connect() {
    // Trigger on init, and on page events
    return merge(of(this.events), this.paginator.page).pipe(
      map(() => {
        const startIndex = this.paginator.pageIndex * this.paginator.pageSize;
        return this.events.slice(startIndex, startIndex + this.paginator.pageSize);
      })
    );
  }

  disconnect() {}
}
