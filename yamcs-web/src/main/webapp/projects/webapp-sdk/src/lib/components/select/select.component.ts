import { AsyncPipe, NgForOf, NgIf } from '@angular/common';
import { ChangeDetectionStrategy, Component, Input, OnChanges, forwardRef } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { MatDivider } from '@angular/material/divider';
import { MatIcon } from '@angular/material/icon';
import { MatMenu, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { BehaviorSubject } from 'rxjs';

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
    AsyncPipe,
    MatDivider,
    MatIcon,
    MatMenu,
    MatMenuItem,
    MatMenuTrigger,
    NgForOf,
    NgIf,
  ],
})
export class YaSelect implements OnChanges, ControlValueAccessor {

  @Input()
  emptyOption: string = '-- select an option --';

  @Input()
  options: YaSelectOption[] = [];

  @Input()
  icon: string;

  options$ = new BehaviorSubject<YaSelectOption[]>([]);
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
