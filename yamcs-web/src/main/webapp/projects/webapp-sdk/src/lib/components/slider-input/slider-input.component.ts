import {
  ChangeDetectionStrategy,
  Component,
  forwardRef,
  input,
} from '@angular/core';
import {
  ControlValueAccessor,
  FormControl,
  NG_VALUE_ACCESSOR,
  ReactiveFormsModule,
} from '@angular/forms';

@Component({
  selector: 'ya-slider-input',
  templateUrl: './slider-input.component.html',
  styleUrl: './slider-input.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ya-slider-input',
  },
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => YaSliderInput),
      multi: true,
    },
  ],
  imports: [ReactiveFormsModule],
})
export class YaSliderInput implements ControlValueAccessor {
  label = input<string>();
  min = input<number>();
  max = input<number>();
  width = input<number>(100);

  rangeControl = new FormControl<number | null>(null);

  private onChange = (_: number | null) => {};

  constructor() {
    this.rangeControl.valueChanges.subscribe((value) => {
      this.onChange(value);
    });
  }

  writeValue(obj: any): void {
    this.rangeControl.setValue(obj);
  }

  registerOnChange(fn: any): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: any): void {}
}
