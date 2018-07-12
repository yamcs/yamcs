import { AfterViewInit, ChangeDetectionStrategy, Component, ViewChild } from '@angular/core';
import { MatSort, MatTableDataSource } from '@angular/material';
import { Title } from '@angular/platform-browser';
import { Service } from '@yamcs/client';
import { YamcsService } from '../../core/services/YamcsService';



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

  startService(name: string, className: string) {
    if (confirm(`Are you sure you want to start ${name} ?`)) {
      this.yamcs.getInstanceClient()!.startService(className).then(() => {
        this.refreshDataSource();
      });
    }
  }

  stopService(name: string, className: string) {
    if (confirm(`Are you sure you want to stop ${name} ?`)) {
      this.yamcs.getInstanceClient()!.stopService(className).then(() => {
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
