import { ChangeDetectionStrategy, Component, ElementRef, forwardRef, ViewChild } from '@angular/core';
import { ControlValueAccessor, NG_VALIDATORS, NG_VALUE_ACCESSOR, UntypedFormControl, Validator } from '@angular/forms';
import { requireHex } from './validators';

@Component({
  selector: 'app-binary-input',
  templateUrl: './BinaryInput.html',
  styleUrls: ['./BinaryInput.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => BinaryInput),
      multi: true,
    }, {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => BinaryInput),
      multi: true,
    },
  ]
})
export class BinaryInput implements ControlValueAccessor, Validator {

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
