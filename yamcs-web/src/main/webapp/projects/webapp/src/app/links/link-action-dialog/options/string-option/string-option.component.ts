import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, forwardRef, input } from '@angular/core';
import { AbstractControl, ControlValueAccessor, FormControl, NG_VALIDATORS, NG_VALUE_ACCESSOR, ValidationErrors, Validator, ValidatorFn, Validators } from '@angular/forms';
import { Option, WebappSdkModule } from '@yamcs/webapp-sdk';
import { Subscription } from 'rxjs';

@Component({
  standalone: true,
  selector: 'app-string-option',
  templateUrl: './string-option.component.html',
  styleUrls: ['../options.css', './string-option.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => StringOptionComponent),
    multi: true,
  }, {
    provide: NG_VALIDATORS,
    useExisting: forwardRef(() => StringOptionComponent),
    multi: true,
  }],
})
export class StringOptionComponent implements ControlValueAccessor, Validator, OnInit, OnDestroy {

  option = input.required<Option>();

  formControl = new FormControl<string | null>(null);

  private validators: ValidatorFn[] = [];
  private onChange = (_: string | null) => { };
  private subscriptions: Subscription[] = [];

  ngOnInit(): void {
    this.subscriptions.push(
      this.formControl.valueChanges.subscribe(() => {
        const value = this.formControl.value;
        this.onChange(value);
      })
    );

    if (this.option().required) {
      this.validators.push(Validators.required);
    }
  }

  writeValue(obj: any): void {
    this.formControl.setValue(obj);
  }

  registerOnChange(fn: any): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  validate(control: AbstractControl<any, any>): ValidationErrors | null {
    for (const validator of this.validators) {
      const errors = validator(control);
      if (errors) {
        return errors;
      }
    }
    return null;
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach(s => s.unsubscribe());
  }
}
