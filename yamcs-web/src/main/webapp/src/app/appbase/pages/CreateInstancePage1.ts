import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { InstanceTemplate } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';


@Component({
  templateUrl: './CreateInstancePage1.html',
  styleUrls: ['./CreateInstancePage1.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateInstancePage1 {

  dataSource = new MatTableDataSource<InstanceTemplate>([]);

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
