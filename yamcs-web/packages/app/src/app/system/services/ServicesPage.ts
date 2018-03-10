import { Component, ChangeDetectionStrategy, AfterViewInit, ViewChild } from '@angular/core';

import { Service } from '@yamcs/client';

import { YamcsService } from '../../core/services/YamcsService';
import { MatSort, MatTableDataSource } from '@angular/material';

@Component({
  templateUrl: './ServicesPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ServicesPage implements AfterViewInit {

  @ViewChild(MatSort)
  sort: MatSort;

  displayedColumns = ['state', 'name', 'className', 'actions'];

  dataSource = new MatTableDataSource<Service>();

  constructor(private yamcs: YamcsService) {
    this.refreshDataSource();
  }

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
  }

  startService(name: string) {
    if (confirm(`Are you sure you want to start service ${name} ?`)) {
      this.yamcs.getSelectedInstance().startService(name).then(() => {
        this.refreshDataSource();
      });
    }
  }

  stopService(name: string) {
    if (confirm(`Are you sure you want to stop service ${name} ?`)) {
      this.yamcs.getSelectedInstance().stopService(name).then(() => {
        this.refreshDataSource();
      });
    }
  }

  private refreshDataSource() {
    this.yamcs.getSelectedInstance().getServices().then(services => {
      this.dataSource.data = services;
    });
  }
}
