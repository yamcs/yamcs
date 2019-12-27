import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'app-send-command-wizard-step',
  templateUrl: './SendCommandWizardStep.html',
  styleUrls: ['./SendCommandWizardStep.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SendCommandWizardStep {

  @Input()
  step: number;
}
