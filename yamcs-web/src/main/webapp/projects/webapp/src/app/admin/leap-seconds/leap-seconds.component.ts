import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import {
  ValidityRange,
  WebappSdkModule,
  YamcsService,
} from '@yamcs/webapp-sdk';
import { AdminPageComponent } from '../shared/admin-page/admin-page.component';
import { AppAdminToolbar } from '../shared/admin-toolbar/admin-toolbar.component';

@Component({
  templateUrl: './leap-seconds.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AdminPageComponent, AppAdminToolbar, WebappSdkModule],
})
export class LeapSecondsComponent {
  displayedColumns = [
    'period',
    'leap-seconds',
    'tai-utc',
    //'utc-tai',
    'actions',
  ];

  dataSource = new MatTableDataSource<ValidityRange>();

  constructor(yamcs: YamcsService, title: Title) {
    title.setTitle('Leap seconds');
    yamcs.yamcsClient.getLeapSeconds().then((table) => {
      this.dataSource.data = table.ranges;
    });
  }
}
