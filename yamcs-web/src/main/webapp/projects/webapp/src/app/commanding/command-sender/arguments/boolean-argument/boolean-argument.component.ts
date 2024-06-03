import { ChangeDetectionStrategy, Component, forwardRef, Input, OnDestroy, OnInit } from '@angular/core';
import { ControlValueAccessor, FormControl, FormGroup, NG_VALIDATORS, NG_VALUE_ACCESSOR, UntypedFormControl, ValidationErrors, Validator, ValidatorFn, Validators } from '@angular/forms';
import { ArgumentType, utils, WebappSdkModule } from '@yamcs/webapp-sdk';
import { Subscription } from 'rxjs';

@Component({
  standalone: true,
  selector: 'app-boolean-argument',
  templateUrl: './boolean-argument.component.html',
  styleUrls: ['../arguments.css', './boolean-argument.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => BooleanArgumentComponent),
    multi: true,
  }, {
    provide: NG_VALIDATORS,
    useExisting: forwardRef(() => BooleanArgumentComponent),
    multi: true,
  }],
  imports: [
    WebappSdkModule,
  ],
})
export class BooleanArgumentComponent implements ControlValueAccessor, OnInit, Validator, OnDestroy {

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

  // Wrap in a group to avoid interference between multiple
  // BooleanArgument instances. See #729
  formGroup: FormGroup;

  controlName: string;

  private validators: ValidatorFn[] = [];
  private onChange = (_: string | null) => { };
  private subscriptions: Subscription[] = [];

  constructor() {
    this.formGroup = new FormGroup({
      enabled: new FormControl(null),
    });
  }

  ngOnInit() {
    this.subscriptions.push(
      this.formGroup.valueChanges.subscribe(() => {
        let value = this.formGroup.get('enabled')!.value;
        this.onChange(value);
      })
    );

    if (this.index === undefined) {
      this.controlName = this.name;
    } else {
      this.controlName = String(this.index);
    }

    this.validators.push(Validators.required);
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
    if (obj === 'true') {
      this.formGroup.setValue({ 'enabled': 'true' });
    } else if (obj === 'false') {
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
