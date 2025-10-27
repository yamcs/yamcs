import {
  ChangeDetectionStrategy,
  Component,
  computed,
  contentChildren,
  forwardRef,
  input,
  signal,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { YaButtonGroup } from '../button-group/button-group.component';
import { YaButton } from '../button/button.component';
import { YaOption } from '../option/option.component';
import { YaSelectOption } from '../select/select.component';

@Component({
  selector: 'ya-inline-select',
  templateUrl: './inline-select.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => YaInlineSelect),
      multi: true,
    },
  ],
  imports: [YaButton, YaButtonGroup],
})
export class YaInlineSelect implements ControlValueAccessor {
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

  private onChange = (_: string | null) => {};

  public isSelected(id: string) {
    const value = this.selected();
    if (id === '') {
      return value === id || value === null;
    }
    return value === id;
  }

  clearValue() {
    this.writeValue(null);
  }

  writeValue(value: any) {
    this.selected.set(value);
    this.onChange(value);
  }

  registerOnChange(fn: any) {
    this.onChange = fn;
  }

  registerOnTouched(fn: any) {}
}
