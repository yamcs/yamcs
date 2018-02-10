import { Component, ChangeDetectionStrategy, OnInit, ViewChild } from '@angular/core';

import { BehaviorSubject } from 'rxjs/BehaviorSubject';
import { YamcsService } from '../../core/services/yamcs.service';
import { MatPaginator } from '@angular/material';
import { DataSource } from '@angular/cdk/table';

import { Event } from '../../../yamcs-client';

@Component({
  templateUrl: './events.component.html',
  styleUrls: ['./events.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EventsPageComponent implements OnInit {

  pageIndex = 0;
  pageSize = 25;

  @ViewChild(MatPaginator)
  paginator: MatPaginator;

  displayedColumns = ['severity', 'message', 'type', 'source', 'gentime', 'rectime'];
  dataSource: EventDataSource | null;

  events$ = new BehaviorSubject<Event[]>([]);

  constructor(private yamcs: YamcsService) {
    this.yamcs.getSelectedInstance().getEventUpdates().subscribe(evt => {
      // this.processEvent(evt);
    });
  }

  ngOnInit() {
    this.yamcs.getSelectedInstance().getEvents().subscribe(events => {
      this.dataSource = new EventDataSource(events, this.paginator);
    });
  }

  /*private processEvent(evt: Event) {
    const events = this.events$.getValue().slice();
    events.push(evt);
    this.events$.next(events);
  }*/
}

class EventDataSource extends DataSource<Event> {
  dataChange = new BehaviorSubject<Event[]>([]);

  constructor(events: Event[], paginator: MatPaginator) {
    super();
    this.dataChange.next(events);
  }

  connect() {
    return this.dataChange;
  }

  disconnect() {}
}
