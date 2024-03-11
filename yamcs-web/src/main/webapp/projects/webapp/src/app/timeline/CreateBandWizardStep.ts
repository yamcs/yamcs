import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'app-create-band-wizard-step',
  templateUrl: './CreateBandWizardStep.html',
  styleUrl: './CreateBandWizardStep.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CreateBandWizardStep {

  @Input()
  step: string;
}
