import { booleanAttribute, ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  standalone: true,
  selector: 'ya-option',
  template: '',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class YaOption {

  id = input.required<string>();
  label = input.required<string>();
  icon = input<string>();
  group = input(false, { transform: booleanAttribute });
}
