import { ChangeDetectionStrategy, Component, forwardRef, Input } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { BehaviorSubject } from 'rxjs';

export interface Option {
  id: string;
  label: string;
  group?: boolean;
}

@Component({
  selector: 'app-multi-select',
  templateUrl: './MultiSelect.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => MultiSelect),
      multi: true,
    }
  ]
})
export class MultiSelect implements ControlValueAccessor {

  @Input()
  emptyOption: string = '-- select an option --';

  @Input()
  options: Option[] = [];

  @Input()
  icon: string;

  selected$ = new BehaviorSubject<string[]>([]);

  private onChange = (_: string[]) => { };

  public isSelected(id: string) {
    return this.selected$.value.indexOf(id) !== -1;
  }

  writeValue(value: any) {
    this.selected$.next(value);
    this.onChange(value);
  }

  enableOption(event: MouseEvent, id: string) {
    const selected = [...this.selected$.value];
    if (selected.indexOf(id) === -1) {
      selected.push(id);
    }
    this.writeValue(selected);

    event.preventDefault();
    event.stopPropagation();
    return false;
  }

  disableOption(event: MouseEvent, id: string) {
    const selected = [...this.selected$.value];
    const idx = selected.indexOf(id);
    if (idx !== -1) {
      selected.splice(idx, 1);
    }
    this.writeValue(selected);

    event.preventDefault();
    event.stopPropagation();
    return false;
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
