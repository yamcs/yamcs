import { ChangeDetectionStrategy, Component, computed, contentChildren, forwardRef, input, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { MatDivider } from '@angular/material/divider';
import { MatIcon } from '@angular/material/icon';
import { MatMenu, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { YaButton } from '../button/button.component';
import { YaOption } from '../option/option.component';

export interface YaSelectOption {
  id: string;
  label: string;
  group?: boolean;
  icon?: string;
}

@Component({
  standalone: true,
  selector: 'ya-select',
  templateUrl: './select.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => YaSelect),
    multi: true,
  }],
  imports: [
    MatDivider,
    MatIcon,
    MatMenu,
    MatMenuItem,
    MatMenuTrigger,
    YaButton,
  ],
})
export class YaSelect implements ControlValueAccessor {

  icon = input<string>();
  emptyOption = input<string>('-- select an option --');

  // Options are allowed to be provided as children, or
  // in a single attribute.
  options = input<YaSelectOption[]>([]);
  optionChildren = contentChildren(YaOption);

  selected = signal<string | null>(null);

  label = computed(() => {
    const selectedId = this.selected() || '';

    for (const option of this.options()) {
      if (option.id === selectedId) {
        return option.label || option.id;
      }
    }
    for (const option of this.optionChildren()) {
      if (option.id() === selectedId) {
        return option.label() || option.id();
      }
    }
    return selectedId;
  });

  private onChange = (_: string | null) => { };

  public isSelected(id: string) {
    const value = this.selected();
    if (id === '') {
      return value === id || value === null;
    }
    return value === id;
  }

  writeValue(value: any) {
    this.selected.set(value);
    this.onChange(value);
  }

  registerOnChange(fn: any) {
    this.onChange = fn;
  }

  registerOnTouched(fn: any) {
  }
}
