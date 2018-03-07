import { Component, ChangeDetectionStrategy, OnInit, AfterViewInit, ViewChild } from '@angular/core';
import { Observable } from 'rxjs/Observable';

import { Stream, Instance } from '../../../yamcs-client';

import { YamcsService } from '../../core/services/YamcsService';
import { MatTableDataSource, MatSort } from '@angular/material';
import { State } from '../../app.reducers';
import { Store } from '@ngrx/store';
import { selectCurrentInstance } from '../../core/store/instance.selectors';

@Component({
  templateUrl: './StreamsPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StreamsPage implements OnInit, AfterViewInit {

  @ViewChild(MatSort)
  sort: MatSort;

  instance$: Observable<Instance>;

  displayedColumns = ['name'];

  dataSource = new MatTableDataSource<Stream>();

  constructor(yamcs: YamcsService, private store: Store<State>) {
    yamcs.getSelectedInstance().getStreams().subscribe(streams => {
      this.dataSource.data = streams;
    });
  }

  ngOnInit() {
    this.instance$ = this.store.select(selectCurrentInstance);
  }

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
  }
}
