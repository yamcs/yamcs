import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  input,
  output,
} from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';

@Component({
  selector: 'ya-page-button',
  templateUrl: './page-button.component.html',
  styleUrl: './page-button.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButton, MatIcon],
  host: {
    class: 'ya-page-button',
    '[class.disabled]': 'disabled()',
  },
})
export class YaPageButton {
  icon = input<string>();
  iconRotate90 = input(false, { transform: booleanAttribute });
  disabled = input(false, { transform: booleanAttribute });
  dropdown = input(false, { transform: booleanAttribute });
  color = input<string>('primary');
  primary = input(false, { transform: booleanAttribute });

  clicked = output<MouseEvent>();

  onClick(event: MouseEvent) {
    if (!this.disabled()) {
      this.clicked.emit(event);
    }
  }
}
