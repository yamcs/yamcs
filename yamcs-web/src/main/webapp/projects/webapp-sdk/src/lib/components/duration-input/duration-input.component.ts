import { ChangeDetectionStrategy, Component, ElementRef, forwardRef, ViewChild } from '@angular/core';
import { ControlValueAccessor, NG_VALIDATORS, NG_VALUE_ACCESSOR, ReactiveFormsModule, UntypedFormControl, ValidationErrors, Validator, Validators } from '@angular/forms';
import { YaSelect, YaSelectOption } from '../select/select.component';

// Used as a signal to show validation results
const INVALID_PROTOSTRING = 'invalid';

@Component({
  standalone: true,
  selector: 'ya-duration-input',
  templateUrl: './duration-input.component.html',
  styleUrl: './duration-input.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => YaDurationInput),
    multi: true,
  }, {
    provide: NG_VALIDATORS,
    useExisting: forwardRef(() => YaDurationInput),
    multi: true,
  }],
  imports: [
    ReactiveFormsModule,
    YaSelect,
  ],
})
export class YaDurationInput implements ControlValueAccessor, Validator {

  resolutionOptions: YaSelectOption[] = [
    { id: 'seconds', label: 'seconds' },
    { id: 'minutes', label: 'minutes' },
    { id: 'hours', label: 'hours' }
  ];

  @ViewChild('input', { static: true })
  private inputComponent: ElementRef;

  private onChange = (_: string | null) => { };

  resolutionControl: UntypedFormControl;

  constructor() {
    this.resolutionControl = new UntypedFormControl('seconds', Validators.required);
    this.resolutionControl.valueChanges.subscribe(() => this.fireChange());
  }

  writeValue(value: any) {
    if (value) {
      // Don't show trailing 's'
      const seconds = value.substring(0, value.length - 1);
      this.inputComponent.nativeElement.value = seconds;
      this.fireChange();
    }
  }

  fireChange() {
    try {
      const duration = this.createDurationOrThrow();
      this.onChange(duration || null);
    } catch {
      // Trigger a validation error
      this.onChange(INVALID_PROTOSTRING);
    }
  }

  registerOnChange(fn: any) {
    this.onChange = fn;
  }

  registerOnTouched(fn: any) {
  }

  validate(control: UntypedFormControl): ValidationErrors | null {
    if (control.value === INVALID_PROTOSTRING) {
      return { duration: true };
    }
    return null;
  }

  private createDurationOrThrow() {
    const durationInput = this.inputComponent.nativeElement.value;
    const resolutionInput = this.resolutionControl.value;
    if (durationInput === '' || !resolutionInput) {
      return null;
    }

    if (!isFloat(durationInput)) {
      throw new Error('Invalid duration pattern');
    }

    let durationInSeconds;
    switch (resolutionInput) {
      case 'seconds':
        durationInSeconds = durationInput;
        break;
      case 'minutes':
        durationInSeconds = durationInput * 60;
        break;
      case 'hours':
        durationInSeconds = durationInput * 60 * 60;
        break;
      default:
        throw new Error(`Unexpected resolution ${resolutionInput}`);
    }

    return durationInSeconds + 's';
  }
}

function isFloat(value: any) {
  return !isNaN(value) && (Number.isInteger(parseFloat(value)) || value % 1 !== 0);
}
