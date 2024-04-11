import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { InstancePageTemplateComponent } from '../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../shared/instance-toolbar/instance-toolbar.component';
import { CreateBandWizardStepComponent } from '../create-band-wizard-step/create-band-wizard-step.component';

@Component({
  standalone: true,
  templateUrl: './create-band.component.html',
  styleUrl: './create-band.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CreateBandWizardStepComponent,
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    WebappSdkModule,
  ],
})
export class CreateBandComponent {

  constructor(
    title: Title,
    readonly yamcs: YamcsService,
  ) {
    title.setTitle('Create a Band');
  }
}
