import { Component, ChangeDetectionStrategy, AfterViewInit, ViewChild } from '@angular/core';

import { Service } from '@yamcs/client';

import { YamcsService } from '../../core/services/YamcsService';
import { MatSort, MatTableDataSource } from '@angular/material';
import { Title } from '@angular/platform-browser';

@Component({
  templateUrl: './ServicesPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ServicesPage implements AfterViewInit {

  @ViewChild(MatSort)
  sort: MatSort;

  displayedColumns = ['state', 'name', 'className', 'actions'];

  dataSource = new MatTableDataSource<Service>();

  constructor(private yamcs: YamcsService, title: Title) {
    title.setTitle('Services - Yamcs');
    this.refreshDataSource();
  }

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
  }

  startService(name: string) {
    if (confirm(`Are you sure you want to start service ${name} ?`)) {
      this.yamcs.getInstanceClient()!.startService(name).then(() => {
        this.refreshDataSource();
      });
    }
  }

  stopService(name: string) {
    if (confirm(`Are you sure you want to stop service ${name} ?`)) {
      this.yamcs.getInstanceClient()!.stopService(name).then(() => {
        this.refreshDataSource();
      });
    }
  }

  private refreshDataSource() {
    this.yamcs.getInstanceClient()!.getServices().then(services => {
      this.dataSource.data = services;
    });
  }
}
