import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { SharedModule } from '../../shared/SharedModule';

@Component({
  standalone: true,
  selector: 'app-create-band-wizard-step',
  templateUrl: './create-band-wizard-step.component.html',
  styleUrl: './create-band-wizard-step.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    SharedModule,
  ],
})
export class CreateBandWizardStepComponent {

  @Input()
  step: string;
}
