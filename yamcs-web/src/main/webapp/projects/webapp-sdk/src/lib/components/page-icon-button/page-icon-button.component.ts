import { booleanAttribute, ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';

@Component({
  standalone: true,
  selector: 'ya-page-icon-button',
  templateUrl: './page-icon-button.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatIconButton,
    MatIcon,
  ],
})
export class YaPageIconButton {

  icon = input.required<string>();
  iconRotate90 = input(false, { transform: booleanAttribute });
  disabled = input(false, { transform: booleanAttribute });
  color = input<string>('primary');

  click = output<MouseEvent>();

  onClick(event: MouseEvent) {
    this.click.emit(event);
    event.stopPropagation();
  }
}
