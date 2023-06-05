import { ChangeDetectionStrategy, Component, ElementRef, forwardRef, ViewChild } from '@angular/core';
import { ControlValueAccessor, NG_VALIDATORS, NG_VALUE_ACCESSOR, UntypedFormControl, Validator } from '@angular/forms';
import { requireHex } from '../../validators';

@Component({
  selector: 'ya-binary-input',
  templateUrl: './binary-input.component.html',
  styleUrls: ['./binary-input.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => BinaryInputComponent),
      multi: true,
    }, {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => BinaryInputComponent),
      multi: true,
    },
  ]
})
export class BinaryInputComponent implements ControlValueAccessor, Validator {

  @ViewChild('input', { static: true })
  private inputComponent: ElementRef;

  private onChange = (_: string | null) => { };

  writeValue(value: any) {
    this.inputComponent.nativeElement.value = value;
    this.onChange(value);
  }

  fireChange() {
    const value = this.inputComponent.nativeElement.value;
    this.onChange(value);
  }

  registerOnChange(fn: any) {
    this.onChange = fn;
  }

  registerOnTouched(fn: any) {
  }

  validate(control: UntypedFormControl) {
    return requireHex(control);
  }
}
