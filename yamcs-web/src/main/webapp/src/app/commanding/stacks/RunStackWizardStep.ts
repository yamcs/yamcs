import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'app-run-stack-wizard-step',
  templateUrl: './RunStackWizardStep.html',
  styleUrls: ['./RunStackWizardStep.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RunStackWizardStep {

  @Input()
  step: number;
}
