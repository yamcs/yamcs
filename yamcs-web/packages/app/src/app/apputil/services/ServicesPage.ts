import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatTableDataSource } from '@angular/material';
import { Title } from '@angular/platform-browser';
import { Service } from '@yamcs/client';
import { YamcsService } from '../../core/services/YamcsService';



@Component({
  templateUrl: './ServicesPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ServicesPage {

  dataSource = new MatTableDataSource<Service>();

  constructor(private yamcs: YamcsService, title: Title) {
    title.setTitle('Services - Yamcs');
    this.refreshDataSources();
  }

  startService(name: string) {
    if (confirm(`Are you sure you want to start ${name} ?`)) {
      this.yamcs.yamcsClient.startService(name).then(() => {
        this.refreshDataSources();
      });
    }
  }

  stopService(name: string) {
    if (confirm(`Are you sure you want to stop ${name} ?`)) {
      this.yamcs.yamcsClient.stopService(name).then(() => {
        this.refreshDataSources();
      });
    }
  }

  private refreshDataSources() {
    this.yamcs.yamcsClient.getServices().then(services => {
      this.dataSource.data = services;
    });
  }
}
