import { AfterViewInit, ChangeDetectionStrategy, Component, OnDestroy, ViewChild } from '@angular/core';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { ActivatedRoute } from '@angular/router';
import { StreamEvent, StreamStatisticsSubscription, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';

export interface StreamItem {
  name: string;
  dataCount: number;
}


@Component({
  standalone: true,
  templateUrl: './stream-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class StreamListComponent implements AfterViewInit, OnDestroy {

  @ViewChild(MatSort, { static: true })
  sort: MatSort;

  displayedColumns = ['name', 'dataCount', 'actions'];

  dataSource = new MatTableDataSource<StreamItem>();

  private database: string;
  private streamStatisticsSubscription: StreamStatisticsSubscription;

  private itemsByName: { [key: string]: StreamItem; } = {};

  constructor(route: ActivatedRoute, readonly yamcs: YamcsService) {
    const parent = route.snapshot.parent!;
    this.database = parent.paramMap.get('database')!;
  }

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
    this.streamStatisticsSubscription = this.yamcs.yamcsClient.createStreamStatisticsSubscription({
      instance: this.database,
    }, evt => {
      this.processStreamEvent(evt);
    });
  }

  private processStreamEvent(evt: StreamEvent) {
    switch (evt.type) {
      case 'CREATED':
      case 'UPDATED':
        this.itemsByName[evt.name] = {
          name: evt.name,
          dataCount: evt.dataCount,
        };
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
    this.streamStatisticsSubscription?.cancel();
  }
}
