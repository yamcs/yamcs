import { AfterViewInit, ChangeDetectionStrategy, Component, OnDestroy, ViewChild } from '@angular/core';
import { MatSort, MatTableDataSource } from '@angular/material';
import { Title } from '@angular/platform-browser';
import { Instance, StreamEvent } from '@yamcs/client';
import { Subscription } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';

export interface StreamItem {
  name: string;
  dataCount: number;
}

@Component({
  templateUrl: './StreamsPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StreamsPage implements AfterViewInit, OnDestroy {

  @ViewChild(MatSort)
  sort: MatSort;

  instance: Instance;

  displayedColumns = ['name', 'dataCount'];

  dataSource = new MatTableDataSource<StreamItem>();

  private streamEventSubscription: Subscription;

  private itemsByName: { [key: string]: StreamItem } = {};

  constructor(private yamcs: YamcsService, title: Title) {
    title.setTitle('Streams - Yamcs');
    /*yamcs.getInstanceClient()!.getStreams().then(streams => {
      this.dataSource.data = streams;
    });*/
    this.instance = yamcs.getInstance();
  }

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;

    this.yamcs.getInstanceClient()!.getStreamEventUpdates().then(response => {
      this.streamEventSubscription = response.streamEvent$.subscribe(evt => {
        this.processStreamEvent(evt);
      });
    });
  }

  private processStreamEvent(evt: StreamEvent) {
    switch (evt.type) {
      case 'CREATED':
      case 'UPDATED':
        const item = this.itemsByName[evt.name];
        if (item) {
          item.dataCount = evt.dataCount;
        } else {
          this.itemsByName[evt.name] = {
            name: evt.name,
            dataCount: evt.dataCount,
          };
        }
        this.updateDataSource();
        break;
      case 'DELETED':
        delete this.itemsByName[evt.name];
        this.updateDataSource();
        break;
      default:
        console.error('Unexpected stream update of type ' + evt.type);
        break;
    }
  }

  private updateDataSource() {
    const data = Object.values(this.itemsByName);
    data.sort((x, y) => {
      return x.name.localeCompare(y.name);
    });
    this.dataSource.data = data;
  }

  ngOnDestroy() {
    if (this.streamEventSubscription) {
      this.streamEventSubscription.unsubscribe();
    }
  }
}
