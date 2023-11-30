import { ChangeDetectionStrategy, Component, forwardRef, Input, OnChanges } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { BehaviorSubject } from 'rxjs';

export interface SelectOption {
  id: string;
  label: string;
  group?: boolean;
}

@Component({
  selector: 'ya-select',
  templateUrl: './select.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => SelectComponent),
      multi: true,
    }
  ]
})
export class SelectComponent implements OnChanges, ControlValueAccessor {

  @Input()
  emptyOption: string = '-- select an option --';

  @Input()
  options: SelectOption[] = [];

  @Input()
  icon: string;

  options$ = new BehaviorSubject<SelectOption[]>([]);
  selected$ = new BehaviorSubject<string | null>(null);

  private onChange = (_: string | null) => { };

  ngOnChanges() {
    this.options$.next(this.options);
  }

  public isSelected(id: string) {
    return this.selected$.value === id;
  }

  writeValue(value: any) {
    this.selected$.next(value);
    this.onChange(value);
  }

  registerOnChange(fn: any) {
    this.onChange = fn;
  }

  registerOnTouched(fn: any) {
  }

  getLabel(id: string) {
    const option = this.findOption(id);
    if (option) {
      return option.label || option.id;
    } else {
      return id;
    }
  }

  private findOption(id: string) {
    for (const option of this.options) {
      if (option.id === id) {
        return option;
      }
    }
    return null;
  }
}
