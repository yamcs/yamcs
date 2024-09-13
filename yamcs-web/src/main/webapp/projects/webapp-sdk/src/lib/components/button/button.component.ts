import { booleanAttribute, ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatIcon } from '@angular/material/icon';

export type YaButtonAppearance = 'basic' | 'text' | 'primary';

@Component({
  standalone: true,
  selector: 'ya-button',
  templateUrl: './button.component.html',
  styleUrl: './button.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatIcon,
  ],
})
export class YaButton {

  icon = input<string>();
  appearance = input('basic');
  disabled = input(false, { transform: booleanAttribute });
  dropdown = input(false, { transform: booleanAttribute });
  toggled = input(false, { transform: booleanAttribute });

  click = output<MouseEvent>();

  onClick(event: MouseEvent) {
    this.click.emit(event);
    event.stopPropagation();
  }
}
