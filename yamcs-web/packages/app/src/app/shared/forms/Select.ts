import { ChangeDetectionStrategy, Component, forwardRef, Input } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { BehaviorSubject } from 'rxjs';

export interface Option {
  id: string;
  label: string;
  group?: boolean;
}

@Component({
  selector: 'app-select',
  templateUrl: './Select.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => Select),
      multi: true,
    }
  ]
})
export class Select implements ControlValueAccessor {

  @Input()
  options: Option[] = [];

  @Input()
  icon: string;

  selected$ = new BehaviorSubject<string | null>(null);

  private onChange = (_: string | null) => { };

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
