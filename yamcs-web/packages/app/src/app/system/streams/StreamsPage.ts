import { Component, ChangeDetectionStrategy, AfterViewInit, ViewChild } from '@angular/core';

import { Stream, Instance } from '@yamcs/client';

import { YamcsService } from '../../core/services/YamcsService';
import { MatTableDataSource, MatSort } from '@angular/material';
import { Title } from '@angular/platform-browser';

@Component({
  templateUrl: './StreamsPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StreamsPage implements AfterViewInit {

  @ViewChild(MatSort)
  sort: MatSort;

  instance: Instance;

  displayedColumns = ['name'];

  dataSource = new MatTableDataSource<Stream>();

  constructor(yamcs: YamcsService, title: Title) {
    title.setTitle('Streams - Yamcs');
    yamcs.getInstanceClient()!.getStreams().then(streams => {
      this.dataSource.data = streams;
    });
    this.instance = yamcs.getInstance();
  }

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
  }
}
