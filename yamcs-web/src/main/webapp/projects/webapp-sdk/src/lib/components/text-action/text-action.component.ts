import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  input,
} from '@angular/core';
import { MatIcon } from '@angular/material/icon';

@Component({
  selector: 'ya-text-action',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './text-action.component.html',
  styleUrl: './text-action.component.css',
  host: {
    class: 'ya-text-action',
    '[class.active]': 'active()',
    '[class.padding]': 'padding()',
    '[class.disabled]': 'disabled()',
  },
  imports: [MatIcon],
})
export class YaTextAction {
  icon = input<string>();
  active = input(false, { transform: booleanAttribute });
  padding = input(true, { transform: booleanAttribute });
  disabled = input(false, { transform: booleanAttribute });
}
