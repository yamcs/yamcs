import { ChangeDetectionStrategy, Component, input, model } from '@angular/core';

@Component({
  standalone: true,
  selector: 'ya-stepper-step',
  templateUrl: './stepper-step.component.html',
  styleUrl: './stepper-step.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class YaStepperStep {

  label = input.required<string>();
  visible = input(true);
  expanded = model(false);

  toggle() {
    this.expanded.set(!this.expanded());
  }
}
