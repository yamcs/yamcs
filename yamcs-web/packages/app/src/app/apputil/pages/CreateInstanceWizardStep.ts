import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'app-create-instance-wizard-step',
  templateUrl: './CreateInstanceWizardStep.html',
  styleUrls: ['./CreateInstanceWizardStep.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CreateInstanceWizardStep {

  @Input()
  step: number;
}
