import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';

@Component({
  selector: 'ya-form',
  templateUrl: './form.component.html',
  host: {
    // Currently specified in form.css, but expected to eventually be moved
    // in each input's styles.
    class: 'ya-form',
  },
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class YaForm {
  formGroup = input.required<FormGroup>();
}
