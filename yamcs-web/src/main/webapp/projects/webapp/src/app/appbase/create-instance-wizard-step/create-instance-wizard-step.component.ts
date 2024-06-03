import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-create-instance-wizard-step',
  templateUrl: './create-instance-wizard-step.component.html',
  styleUrl: './create-instance-wizard-step.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class CreateInstanceWizardStepComponent {

  @Input()
  step: number;
}
