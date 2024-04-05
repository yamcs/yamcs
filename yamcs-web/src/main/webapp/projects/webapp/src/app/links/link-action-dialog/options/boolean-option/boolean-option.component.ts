import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, forwardRef, input } from '@angular/core';
import { ControlValueAccessor, FormControl, FormGroup, NG_VALIDATORS, NG_VALUE_ACCESSOR, UntypedFormControl, ValidationErrors, Validator, ValidatorFn, Validators } from '@angular/forms';
import { Option, WebappSdkModule } from '@yamcs/webapp-sdk';
import { Subscription } from 'rxjs';

@Component({
  standalone: true,
  selector: 'app-boolean-option',
  templateUrl: './boolean-option.component.html',
  styleUrls: ['../options.css', './boolean-option.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => BooleanOptionComponent),
    multi: true,
  }, {
    provide: NG_VALIDATORS,
    useExisting: forwardRef(() => BooleanOptionComponent),
    multi: true,
  }],
})
export class BooleanOptionComponent implements ControlValueAccessor, Validator, OnInit, OnDestroy {

  option = input.required<Option>();

  // Wrap in a group to avoid interference between multiple
  // component instances. See #729
  formGroup: FormGroup;

  private validators: ValidatorFn[] = [];
  private onChange = (_: boolean | null) => { };
  private subscriptions: Subscription[] = [];

  constructor() {
    this.formGroup = new FormGroup({
      enabled: new FormControl(null),
    });
  }

  ngOnInit() {
    this.subscriptions.push(
      this.formGroup.valueChanges.subscribe(() => {
        const value = this.formGroup.get('enabled')!.value;
        if (value === 'true') {
          this.onChange(true);
        } else if (value === 'false') {
          this.onChange(false);
        } else {
          this.onChange(null);
        }
      })
    );

    if (this.option().required) {
      this.validators.push(Validators.required);
    }
  }

  writeValue(obj: any) {
    if (obj === true) {
      this.formGroup.setValue({ 'enabled': 'true' });
    } else if (obj === false) {
      this.formGroup.setValue({ 'enabled': 'false' });
    }
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
