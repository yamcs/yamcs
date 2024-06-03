import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-create-band-wizard-step',
  templateUrl: './create-band-wizard-step.component.html',
  styleUrl: './create-band-wizard-step.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class CreateBandWizardStepComponent {

  @Input()
  step: string;
}
