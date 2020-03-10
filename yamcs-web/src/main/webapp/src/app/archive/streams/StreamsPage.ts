import { AfterViewInit, ChangeDetectionStrategy, Component, OnDestroy, ViewChild } from '@angular/core';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { StreamEvent, StreamStatisticsSubscription } from '../../client';
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

  @ViewChild(MatSort, { static: true })
  sort: MatSort;

  instance: string;

  displayedColumns = ['name', 'dataCount'];

  dataSource = new MatTableDataSource<StreamItem>();

  private streamStatisticsSubscription: StreamStatisticsSubscription;

  private itemsByName: { [key: string]: StreamItem; } = {};

  constructor(private yamcs: YamcsService, title: Title) {
    title.setTitle('Streams');
    /*yamcs.getInstanceClient()!.getStreams().then(streams => {
      this.dataSource.data = streams;
    });*/
    this.instance = yamcs.getInstance();
  }

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;

    this.streamStatisticsSubscription = this.yamcs.yamcsClient.createStreamStatisticsSubscription({
      instance: this.instance,
    }, evt => {
      this.processStreamEvent(evt);
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
    if (this.streamStatisticsSubscription) {
      this.streamStatisticsSubscription.cancel();
    }
  }
}
