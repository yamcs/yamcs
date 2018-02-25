import { Component, ChangeDetectionStrategy, OnInit, ViewChild, AfterViewInit } from '@angular/core';

import { YamcsService } from '../../core/services/YamcsService';
import { MatPaginator } from '@angular/material';
import { EventsDataSource } from './EventsDataSource';

@Component({
  templateUrl: './EventsPage.html',
  styleUrls: ['./EventsPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EventsPage implements OnInit, AfterViewInit {

  start: Date;
  stop: Date;

  pageIndex = 0;
  pageSize = 25;

  @ViewChild(MatPaginator)
  paginator: MatPaginator;

  dataSource: EventsDataSource;
  displayedColumns = ['severity', 'gentime', 'message', 'type', 'source', 'rectime'];

  constructor(private yamcs: YamcsService) {
  }

  ngOnInit() {
    this.dataSource = new EventsDataSource(this.yamcs, this.paginator);
    this.jumpToNow();
  }

  ngAfterViewInit() {

  }

  jumpToNow() {
    this.stop = new Date(); // TODO use mission time instead.
    this.start = new Date();
    this.start.setUTCHours(this.stop.getUTCHours() - 1);
    this.dataSource.loadEvents(this.start, this.stop);
  }
}

