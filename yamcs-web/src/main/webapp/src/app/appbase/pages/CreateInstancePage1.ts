import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatLegacyTableDataSource } from '@angular/material/legacy-table';
import { Title } from '@angular/platform-browser';
import { InstanceTemplate } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';


@Component({
  templateUrl: './CreateInstancePage1.html',
  styleUrls: ['./CreateInstancePage1.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateInstancePage1 {

  dataSource = new MatLegacyTableDataSource<InstanceTemplate>([]);

  displayedColumns = [
    'name',
    'description',
  ];

  constructor(private yamcs: YamcsService, title: Title) {
    title.setTitle('Create an Instance');
    this.yamcs.yamcsClient.getInstanceTemplates().then(templates => {
      this.dataSource.data = templates;
    });
  }
}
