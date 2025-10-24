import {
  ChangeDetectionStrategy,
  Component,
  input,
  model,
} from '@angular/core';

@Component({
  selector: 'ya-stepper-step',
  templateUrl: './stepper-step.component.html',
  styleUrls: ['./vars.css', './stepper-step.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ya-stepper-step',
    '[class.hidden]': '!visible()',
  },
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
