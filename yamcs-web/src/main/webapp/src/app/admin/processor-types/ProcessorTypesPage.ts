import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatLegacyTableDataSource } from '@angular/material/legacy-table';
import { Title } from '@angular/platform-browser';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './ProcessorTypesPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProcessorTypesPage {

  displayedColumns = ['name'];

  dataSource = new MatLegacyTableDataSource<string>();

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
