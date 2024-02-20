import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { ValidityRange, YamcsService } from '@yamcs/webapp-sdk';

@Component({
  templateUrl: './LeapSecondsPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LeapSecondsPage {

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
