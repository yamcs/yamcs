import { ChangeDetectionStrategy, Component, forwardRef, Input, OnChanges } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { BehaviorSubject } from 'rxjs';
import { SelectOption } from '../select/select.component';

@Component({
  selector: 'ya-multi-select',
  templateUrl: './multi-select.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => MultiSelectComponent),
      multi: true,
    }
  ]
})
export class MultiSelectComponent implements OnChanges, ControlValueAccessor {

  @Input()
  emptyOption: string = '-- select an option --';

  @Input()
  options: SelectOption[] = [];

  @Input()
  icon: string;

  options$ = new BehaviorSubject<SelectOption[]>([]);
  selected$ = new BehaviorSubject<string[]>([]);

  private onChange = (_: string[]) => { };

  ngOnChanges() {
    this.options$.next(this.options);
  }

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

  getLabel(ids: string[]) {
    const labels = ids.map(id => {
      const option = this.findOption(id);
      if (option) {
        return option.label || option.id;
      } else {
        return id;
      }
    });
    return labels.join(', ');
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
