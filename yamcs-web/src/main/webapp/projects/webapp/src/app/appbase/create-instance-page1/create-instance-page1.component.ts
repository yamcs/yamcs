import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { InstanceTemplate, YamcsService } from '@yamcs/webapp-sdk';
import { CreateInstanceWizardStepComponent } from '../create-instance-wizard-step/create-instance-wizard-step.component';

import { WebappSdkModule } from '@yamcs/webapp-sdk';
import { AppAppBaseToolbarLabel } from '../appbase-toolbar/appbase-toolbar-label.directive';
import { AppAppBaseToolbar } from '../appbase-toolbar/appbase-toolbar.component';

@Component({
  templateUrl: './create-instance-page1.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AppAppBaseToolbar,
    AppAppBaseToolbarLabel,
    CreateInstanceWizardStepComponent,
    WebappSdkModule,
  ],
})
export class CreateInstancePage1Component {
  dataSource = new MatTableDataSource<InstanceTemplate>([]);

  displayedColumns = ['name', 'description'];

  constructor(
    private yamcs: YamcsService,
    title: Title,
  ) {
    title.setTitle('Create an instance');
    this.yamcs.yamcsClient.getInstanceTemplates().then((templates) => {
      this.dataSource.data = templates;
    });
  }
}
