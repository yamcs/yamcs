import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { AdminPageComponent } from '../shared/admin-page/admin-page.component';
import { AppAdminToolbar } from '../shared/admin-toolbar/admin-toolbar.component';

@Component({
  templateUrl: './processor-types.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AdminPageComponent, AppAdminToolbar, WebappSdkModule],
})
export class ProcessorTypesComponent {
  displayedColumns = ['name'];

  dataSource = new MatTableDataSource<string>();

  constructor(yamcs: YamcsService, title: Title) {
    title.setTitle('Processor types');
    yamcs.yamcsClient.getProcessorTypes().then((response) => {
      this.dataSource.data = response.types || [];
    });
  }
}
