import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  input,
} from '@angular/core';
import { MatIcon } from '@angular/material/icon';

@Component({
  selector: 'ya-icon-action',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './icon-action.component.html',
  styleUrl: './icon-action.component.css',
  host: {
    class: 'ya-icon-action',
    '[class.padding]': 'padding()',
    '[class.disabled]': 'disabled()',
  },
  imports: [MatIcon],
})
export class YaIconAction {
  icon = input.required<string>();
  padding = input(true, { transform: booleanAttribute });
  disabled = input(false, { transform: booleanAttribute });
}
