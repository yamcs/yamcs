import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { YamcsService } from '@yamcs/webapp-sdk';

@Component({
  templateUrl: './ProcessorTypesPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProcessorTypesPage {

  displayedColumns = ['name'];

  dataSource = new MatTableDataSource<string>();

  constructor(
    yamcs: YamcsService,
    title: Title,
  ) {
    title.setTitle('Processor types');
    yamcs.yamcsClient.getProcessorTypes().then(response => {
      this.dataSource.data = response.types || [];
    });
  }
}
