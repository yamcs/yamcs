import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { ValidityRange, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { AdminPageTemplateComponent } from '../shared/admin-page-template/admin-page-template.component';
import { AdminToolbarComponent } from '../shared/admin-toolbar/admin-toolbar.component';

@Component({
  standalone: true,
  templateUrl: './leap-seconds.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AdminPageTemplateComponent,
    AdminToolbarComponent,
    WebappSdkModule,
  ],
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
    yamcs.yamcsClient.getLeapSeconds().then(table => {
      this.dataSource.data = table.ranges;
    });
  }
}
