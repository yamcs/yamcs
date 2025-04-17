import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  input,
  output,
} from '@angular/core';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';

@Component({
  selector: 'ya-page-icon-button',
  templateUrl: './page-icon-button.component.html',
  styleUrl: './page-icon-button.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatIconButton, MatIcon],
  host: {
    class: 'ya-page-icon-button',
    '[class.disabled]': 'disabled()',
  },
})
export class YaPageIconButton {
  icon = input.required<string>();
  iconRotate90 = input(false, { transform: booleanAttribute });
  disabled = input(false, { transform: booleanAttribute });
  color = input<string>('primary');

  clicked = output<MouseEvent>();

  onClick(event: MouseEvent) {
    if (!this.disabled()) {
      this.clicked.emit(event);
    }
  }
}
