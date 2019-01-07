import { Component, ChangeDetectionStrategy, ViewChild, AfterViewInit, OnDestroy } from '@angular/core';

import { Processor, Instance } from '@yamcs/client';

import { YamcsService } from '../../core/services/YamcsService';
import { MatTableDataSource, MatSort } from '@angular/material';
import { Title } from '@angular/platform-browser';
import { Subscription } from 'rxjs';

@Component({
  templateUrl: './ProcessorsPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProcessorsPage implements AfterViewInit, OnDestroy {

  @ViewChild(MatSort)
  sort: MatSort;

  displayedColumns = ['name', 'type', 'creator', 'persistent', 'time', 'state'];

  instance: Instance;
  dataSource = new MatTableDataSource<Processor>();

  processorSubscription: Subscription;

  private processorsByName: { [key: string]: Processor } = {};

  constructor(yamcs: YamcsService, title: Title) {
    title.setTitle('Processors - Yamcs');
    yamcs.getInstanceClient()!.getProcessors().then(processors => {
      for (const processor of processors) {
        this.processProcessorEvent(processor);
      }
    });

    yamcs.getInstanceClient()!.getProcessorUpdates().then(response => {
      this.processorSubscription = response.processor$.subscribe(processor => {
        this.processProcessorEvent(processor);
      });
    });

    this.instance = yamcs.getInstance();
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
