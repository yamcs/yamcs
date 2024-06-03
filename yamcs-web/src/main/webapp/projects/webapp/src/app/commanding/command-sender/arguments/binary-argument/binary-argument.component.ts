import { ChangeDetectionStrategy, Component, forwardRef, Input, OnDestroy, OnInit } from '@angular/core';
import { ControlValueAccessor, NG_VALIDATORS, NG_VALUE_ACCESSOR, UntypedFormControl, ValidationErrors, Validator, ValidatorFn, Validators } from '@angular/forms';
import { ArgumentType, utils, validators, WebappSdkModule } from '@yamcs/webapp-sdk';
import { Subscription } from 'rxjs';

@Component({
  standalone: true,
  selector: 'app-binary-argument',
  templateUrl: './binary-argument.component.html',
  styleUrls: ['../arguments.css', './binary-argument.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => BinaryArgumentComponent),
    multi: true,
  }, {
    provide: NG_VALIDATORS,
    useExisting: forwardRef(() => BinaryArgumentComponent),
    multi: true,
  }],
  imports: [
    WebappSdkModule,
  ],
})
export class BinaryArgumentComponent implements ControlValueAccessor, OnInit, Validator, OnDestroy {

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

    if (this.type.minBytes !== 0) {
      this.validators.push(Validators.required);
    }
    this.validators.push(validators.requireHex);
    if (this.type.minBytes !== undefined) {
      this.validators.push(validators.minHexLengthValidator(this.type.minBytes));
    }
    if (this.type.maxBytes !== undefined) {
      this.validators.push(validators.maxHexLengthValidator(this.type.maxBytes));
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
