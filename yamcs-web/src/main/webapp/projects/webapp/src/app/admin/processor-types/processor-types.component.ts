import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { AdminPageTemplateComponent } from '../shared/admin-page-template/admin-page-template.component';
import { AdminToolbarComponent } from '../shared/admin-toolbar/admin-toolbar.component';

@Component({
  standalone: true,
  templateUrl: './processor-types.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AdminPageTemplateComponent,
    AdminToolbarComponent,
    WebappSdkModule,
  ],
})
export class ProcessorTypesComponent {

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
