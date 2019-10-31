import { ChangeDetectionStrategy, Component, ElementRef, forwardRef, Input, ViewChild } from '@angular/core';
import { ControlValueAccessor, FormControl, NG_VALIDATORS, NG_VALUE_ACCESSOR, Validator, ValidatorFn, Validators } from '@angular/forms';
import * as utils from '../utils';

const ISO_PATTERN = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(.\d{3})?)?Z?$/;
const PATTERN_VALIDATOR = Validators.pattern(ISO_PATTERN);
const DATE_VALIDATOR: ValidatorFn = (control: FormControl) => {
  if (control.value === '') {
    return null;
  }
  try {
    utils.toDate(control.value).toISOString();
    return null; // Parse OK
  } catch {
    return { date: `Invalid date` };
  }
};

@Component({
  selector: 'app-date-time',
  templateUrl: './DateTime.html',
  styleUrls: ['./DateTime.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => DateTime),
      multi: true,
    }, {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => DateTime),
      multi: true,
    },
  ]
})
export class DateTime implements ControlValueAccessor, Validator {

  @Input()
  step = 60; // Default to hh:mm

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

  validate(control: FormControl) {
    if (control.value === '') {
      return null;
    }
    return PATTERN_VALIDATOR(control) || DATE_VALIDATOR(control);
  }
}
