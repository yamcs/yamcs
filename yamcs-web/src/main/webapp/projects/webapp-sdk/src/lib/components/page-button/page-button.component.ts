import { booleanAttribute, ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';

@Component({
  standalone: true,
  selector: 'ya-page-button',
  templateUrl: './page-button.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatButton,
    MatIcon,
  ],
})
export class YaPageButton {

  icon = input<string>();
  iconRotate90 = input(false, { transform: booleanAttribute });
  disabled = input(false, { transform: booleanAttribute });
  dropdown = input(false, { transform: booleanAttribute });
  color = input<string>('primary');

  click = output<MouseEvent>();

  onClick(event: MouseEvent) {
    this.click.emit(event);
    event.stopPropagation();
  }
}
