import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, forwardRef, input } from '@angular/core';
import { ControlValueAccessor, FormControl, NG_VALIDATORS, NG_VALUE_ACCESSOR, UntypedFormControl, ValidationErrors, Validator, ValidatorFn, Validators } from '@angular/forms';
import { Option, WebappSdkModule, validators } from '@yamcs/webapp-sdk';
import { Subscription } from 'rxjs';

@Component({
  standalone: true,
  selector: 'app-float-option',
  templateUrl: './float-option.component.html',
  styleUrls: ['../options.css', './float-option.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => FloatOptionComponent),
    multi: true,
  }, {
    provide: NG_VALIDATORS,
    useExisting: forwardRef(() => FloatOptionComponent),
    multi: true,
  }],
})
export class FloatOptionComponent implements ControlValueAccessor, Validator, OnInit, OnDestroy {

  option = input.required<Option>();

  formControl = new FormControl<string | null>(null);

  private validators: ValidatorFn[] = [];
  private onChange = (_: string | null) => { };
  private subscriptions: Subscription[] = [];

  ngOnInit(): void {
    this.subscriptions.push(
      this.formControl.valueChanges.subscribe(value => {
        if (value === null || value === '') {
          this.onChange(null);
        } else {
          this.onChange(value);
        }
      })
    );

    if (this.option().required) {
      this.validators.push(Validators.required);
    }
    this.validators.push(validators.requireFloat);
  }

  writeValue(obj: any) {
    this.formControl.setValue(obj);
  }

  registerOnChange(fn: any) {
    this.onChange = fn;
  }

  registerOnTouched(fn: any) {
  }

  validate(control: UntypedFormControl): ValidationErrors | null {
    for (const validator of this.validators) {
      const errors = validator(control);
      if (errors) {
        return errors;
      }
    }
    return null;
  }

  ngOnDestroy() {
    this.subscriptions.forEach(s => s.unsubscribe());
  }
}
