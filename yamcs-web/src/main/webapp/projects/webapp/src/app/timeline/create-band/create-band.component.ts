import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { YamcsService } from '@yamcs/webapp-sdk';
import { SharedModule } from '../../shared/SharedModule';
import { CreateBandWizardStepComponent } from '../create-band-wizard-step/create-band-wizard-step.component';

@Component({
  standalone: true,
  templateUrl: './create-band.component.html',
  styleUrl: './create-band.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CreateBandWizardStepComponent,
    SharedModule,
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
