import { ChangeDetectionStrategy, Component, forwardRef, Input, OnDestroy, OnInit } from '@angular/core';
import { ControlValueAccessor, NG_VALIDATORS, NG_VALUE_ACCESSOR, UntypedFormControl, ValidationErrors, Validator, ValidatorFn, Validators } from '@angular/forms';
import { ArgumentType, utils, validators } from '@yamcs/webapp-sdk';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-float-argument',
  templateUrl: './FloatArgument.html',
  styleUrls: ['../arguments.css', './FloatArgument.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => FloatArgument),
    multi: true,
  }, {
    provide: NG_VALIDATORS,
    useExisting: forwardRef(() => FloatArgument),
    multi: true,
  }],
})
export class FloatArgument implements ControlValueAccessor, OnInit, Validator, OnDestroy {

  @Input()
  name: string;

  @Input()
  description?: string;

  @Input()
  type: ArgumentType;

  @Input()
  index?: number;

  @Input()
  dimensions?: number[];

  formControl = new UntypedFormControl();

  controlName: string;

  private validators: ValidatorFn[] = [];
  private onChange = (_: string | null) => { };
  private subscriptions: Subscription[] = [];

  ngOnInit() {
    this.subscriptions.push(
      this.formControl.valueChanges.subscribe(() => {
        let value = this.formControl.value;
        this.onChange(value);
      })
    );

    if (this.index === undefined) {
      this.controlName = this.name;
    } else {
      this.controlName = String(this.index);
    }

    this.validators.push(Validators.required);
    this.validators.push(validators.requireFloat);
    if (this.type.signed === false) {
      this.validators.push(validators.requireUnsigned);
    }
    if (this.type.rangeMax !== undefined) {
      this.validators.push(Validators.max(this.type.rangeMax));
    }
    if (this.type.rangeMin !== undefined) {
      this.validators.push(Validators.min(this.type.rangeMin));
    }
  }

  get label() {
    if (this.index !== undefined) {
      const index = utils.unflattenIndex(this.index, this.dimensions!);
      return index.map(i => '[' + i + ']').join('');
    } else {
      return this.name;
    }
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
