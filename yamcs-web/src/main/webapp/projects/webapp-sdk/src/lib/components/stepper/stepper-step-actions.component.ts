import { ChangeDetectionStrategy, Component } from '@angular/core';
@Component({
  standalone: true,
  selector: 'ya-stepper-step-actions',
  template: '<ng-content />',
  styleUrl: './stepper-step-actions.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class YaStepperStepActions {
}
