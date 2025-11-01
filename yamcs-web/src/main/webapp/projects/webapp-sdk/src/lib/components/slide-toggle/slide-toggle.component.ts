import {
  ChangeDetectionStrategy,
  Component,
  forwardRef,
  model,
  output,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import {
  MatSlideToggle,
  MatSlideToggleChange,
} from '@angular/material/slide-toggle';

@Component({
  selector: 'ya-slide-toggle',
  templateUrl: './slide-toggle.component.html',
  styleUrl: './slide-toggle.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => YaSlideToggle),
      multi: true,
    },
  ],
  imports: [MatSlideToggle],
})
export class YaSlideToggle implements ControlValueAccessor {
  checked = model(false);
  disabled = model(false);

  change = output<MatSlideToggleChange>();
  toggle = output<boolean>();

  private onChange = (value: boolean) => [];

  handleChange(event: MatSlideToggleChange) {
    this.checked.set(event.checked);
    this.onChange(event.checked);
    this.change.emit(event);
    this.toggle.emit(event.checked);
  }

  writeValue(value: any): void {
    this.checked.set(!!value);
  }

  registerOnChange(fn: any): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: any): void {}

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
  }
}
