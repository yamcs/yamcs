import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-send-command-wizard-step',
  templateUrl: './send-command-wizard-step.component.html',
  styleUrl: './send-command-wizard-step.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class SendCommandWizardStepComponent {

  @Input()
  step: number;
}
