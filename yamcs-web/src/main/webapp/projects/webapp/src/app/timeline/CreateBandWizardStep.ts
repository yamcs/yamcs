import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'app-create-band-wizard-step',
  templateUrl: './CreateBandWizardStep.html',
  styleUrls: ['./CreateBandWizardStep.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CreateBandWizardStep {

  @Input()
  step: string;
}
