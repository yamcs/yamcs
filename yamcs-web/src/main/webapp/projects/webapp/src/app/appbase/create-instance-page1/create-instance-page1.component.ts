import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { InstanceTemplate, YamcsService } from '@yamcs/webapp-sdk';
import { SharedModule } from '../../shared/SharedModule';
import { CreateInstanceWizardStepComponent } from '../create-instance-wizard-step/create-instance-wizard-step.component';


@Component({
  standalone: true,
  templateUrl: './create-instance-page1.component.html',
  styleUrl: './create-instance-page1.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CreateInstanceWizardStepComponent,
    SharedModule,
  ],
})
export class CreateInstancePage1Component {

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
