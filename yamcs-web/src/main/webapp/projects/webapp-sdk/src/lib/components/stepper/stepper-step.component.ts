import {
  ChangeDetectionStrategy,
  Component,
  input,
  model,
} from '@angular/core';

@Component({
  selector: 'ya-stepper-step',
  templateUrl: './stepper-step.component.html',
  styleUrl: './stepper-step.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class YaStepperStep {
  label = input.required<string>();
  visible = input(true);
  expanded = model(false);

  toggle(event: MouseEvent) {
    if (!event.target) {
      return;
    }

    if ((event.target as HTMLElement).closest('ya-stepper-step-actions')) {
      // Ignore bubbled up click
      return;
    }

    this.expanded.set(!this.expanded());
  }
}
