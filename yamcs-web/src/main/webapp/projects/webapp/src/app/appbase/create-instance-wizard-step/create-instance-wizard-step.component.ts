import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { SharedModule } from '../../shared/SharedModule';

@Component({
  standalone: true,
  selector: 'app-create-instance-wizard-step',
  templateUrl: './create-instance-wizard-step.component.html',
  styleUrl: './create-instance-wizard-step.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    SharedModule,
  ],
})
export class CreateInstanceWizardStepComponent {

  @Input()
  step: number;
}
