import { Component, ChangeDetectionStrategy, AfterViewInit, ViewChild } from '@angular/core';

import { Service } from '../../../yamcs-client';

import { YamcsService } from '../../core/services/yamcs.service';
import { MatSort, MatTableDataSource } from '@angular/material';

@Component({
  templateUrl: './services.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ServicesPageComponent implements AfterViewInit {

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
      this.yamcs.getSelectedInstance().startService(name).subscribe(() => {
        this.refreshDataSource();
      });
    }
  }

  stopService(name: string) {
    if (confirm(`Are you sure you want to stop service ${name} ?`)) {
      this.yamcs.getSelectedInstance().stopService(name).subscribe(() => {
        this.refreshDataSource();
      });
    }
  }

  private refreshDataSource() {
    this.yamcs.getSelectedInstance().getServices().subscribe(services => {
      this.dataSource.data = services;
    });
  }
}
