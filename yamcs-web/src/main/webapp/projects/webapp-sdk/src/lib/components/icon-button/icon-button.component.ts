import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  input,
  output,
} from '@angular/core';
import { MatIcon } from '@angular/material/icon';

@Component({
  selector: 'ya-icon-button',
  templateUrl: './icon-button.component.html',
  styleUrl: './icon-button.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatIcon],
})
export class YaIconButton {
  icon = input.required<string>();
  disabled = input(false, { transform: booleanAttribute });
  toggled = input(false, { transform: booleanAttribute });

  click = output<MouseEvent>();

  onClick(event: MouseEvent) {
    this.click.emit(event);
    event.stopPropagation();
  }
}
