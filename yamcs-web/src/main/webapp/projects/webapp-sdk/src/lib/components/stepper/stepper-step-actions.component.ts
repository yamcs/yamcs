import { ChangeDetectionStrategy, Component } from '@angular/core';
@Component({
  selector: 'ya-stepper-step-actions',
  template: '<ng-content />',
  styleUrl: './stepper-step-actions.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class YaStepperStepActions {}
