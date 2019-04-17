import { AfterViewInit, ChangeDetectionStrategy, Component, ViewChild } from '@angular/core';
import { MatSort, MatTableDataSource } from '@angular/material';
import { Title } from '@angular/platform-browser';
import { Bucket } from '@yamcs/client';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './GlobalBucketsPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GlobalBucketsPage implements AfterViewInit {

  @ViewChild(MatSort)
  sort: MatSort;

  displayedColumns = [
    'name',
    'size',
    'numObjects',
  ];
  dataSource = new MatTableDataSource<Bucket>();

  constructor(private yamcs: YamcsService, title: Title) {
    title.setTitle('Global Buckets - Yamcs');
    this.refreshDataSources();
  }

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
  }

  private refreshDataSources() {
    const storageClient = this.yamcs.createStorageClient();
    storageClient.getBuckets('_global').then(buckets => {
      this.dataSource.data = buckets;
    });
  }
}
