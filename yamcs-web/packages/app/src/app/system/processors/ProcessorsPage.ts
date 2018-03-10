import { Component, ChangeDetectionStrategy, ViewChild, AfterViewInit, OnInit } from '@angular/core';

import { Processor, Instance } from '@yamcs/client';

import { YamcsService } from '../../core/services/YamcsService';
import { MatTableDataSource, MatSort } from '@angular/material';
import { Observable } from 'rxjs/Observable';
import { selectCurrentInstance } from '../../core/store/instance.selectors';
import { State } from '../../app.reducers';
import { Store } from '@ngrx/store';

@Component({
  templateUrl: './ProcessorsPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProcessorsPage implements OnInit, AfterViewInit {

  @ViewChild(MatSort)
  sort: MatSort;

  displayedColumns = ['name', 'type', 'creator', 'state'];

  instance$: Observable<Instance>;
  dataSource = new MatTableDataSource<Processor>();

  private processorsByName: { [key: string]: Processor } = {};

  constructor(yamcs: YamcsService, private store: Store<State>) {
    yamcs.getSelectedInstance().getProcessors().then(processors => {
      for (const processor of processors) {
        this.processProcessorEvent(processor);
      }
    });

    yamcs.getSelectedInstance().getProcessorUpdates().subscribe(evt => {
      this.processProcessorEvent(evt);
    });
  }

  ngOnInit() {
    this.instance$ = this.store.select(selectCurrentInstance);
  }

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
  }

  private processProcessorEvent(evt: Processor) {
    this.processorsByName[evt.name] = evt;
    this.dataSource.data = Object.values(this.processorsByName);
  }
}
