import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { PluginInfo } from '@yamcs/client';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './PluginsPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PluginsPage {

  displayedColumns = [
    'name',
    'description',
    'version',
    'vendor',
  ];

  dataSource = new MatTableDataSource<PluginInfo>();

  constructor(yamcs: YamcsService, title: Title) {
    title.setTitle('Plugins');
    yamcs.yamcsClient.getGeneralInfo().then(info => {
      this.dataSource.data = info.plugins || [];
    });
  }
}
