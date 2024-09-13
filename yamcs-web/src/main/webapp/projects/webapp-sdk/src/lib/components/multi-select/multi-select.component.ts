import { AsyncPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, Input, OnChanges, forwardRef, input } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { MatDivider } from '@angular/material/divider';
import { MatIcon } from '@angular/material/icon';
import { MatMenu, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { BehaviorSubject } from 'rxjs';
import { YaButton } from '../button/button.component';
import { YaSelectOption } from '../select/select.component';

@Component({
  standalone: true,
  selector: 'ya-multi-select',
  templateUrl: './multi-select.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => YaMultiSelect),
    multi: true,
  }],
  imports: [
    AsyncPipe,
    MatDivider,
    MatIcon,
    MatMenu,
    MatMenuItem,
    MatMenuTrigger,
    YaButton,
  ],
})
export class YaMultiSelect implements OnChanges, ControlValueAccessor {

  @Input()
  options: YaSelectOption[] = [];

  icon = input<string>();
  emptyOption = input<string>('-- select an option --');

  options$ = new BehaviorSubject<YaSelectOption[]>([]);
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
