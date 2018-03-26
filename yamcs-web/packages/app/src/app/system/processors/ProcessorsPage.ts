import { Component, ChangeDetectionStrategy, ViewChild, AfterViewInit, OnInit, OnDestroy } from '@angular/core';

import { Processor, Instance } from '@yamcs/client';

import { YamcsService } from '../../core/services/YamcsService';
import { MatTableDataSource, MatSort } from '@angular/material';
import { Observable } from 'rxjs/Observable';
import { selectCurrentInstance } from '../../core/store/instance.selectors';
import { State } from '../../app.reducers';
import { Store } from '@ngrx/store';
import { Title } from '@angular/platform-browser';
import { Subscription } from 'rxjs/Subscription';

@Component({
  templateUrl: './ProcessorsPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProcessorsPage implements OnInit, AfterViewInit, OnDestroy {

  @ViewChild(MatSort)
  sort: MatSort;

  displayedColumns = ['name', 'type', 'creator', 'state'];

  instance$: Observable<Instance>;
  dataSource = new MatTableDataSource<Processor>();

  processorSubscription: Subscription;

  private processorsByName: { [key: string]: Processor } = {};

  constructor(yamcs: YamcsService, private store: Store<State>, title: Title) {
    title.setTitle('Processors - Yamcs');
    yamcs.getSelectedInstance().getProcessors().then(processors => {
      for (const processor of processors) {
        this.processProcessorEvent(processor);
      }
    });

    yamcs.getSelectedInstance().getProcessorUpdates().then(response => {
      this.processorSubscription = response.processor$.subscribe(processor => {
        this.processProcessorEvent(processor);
      });
    });
  }

  ngOnInit() {
    this.instance$ = this.store.select(selectCurrentInstance);
  }

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
  }

  ngOnDestroy() {
    if (this.processorSubscription) {
      this.processorSubscription.unsubscribe();
    }
  }

  private processProcessorEvent(evt: Processor) {
    this.processorsByName[evt.name] = evt;
    this.dataSource.data = Object.values(this.processorsByName);
  }
}
