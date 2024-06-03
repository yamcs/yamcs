import { ChangeDetectionStrategy, Component, forwardRef, Input, OnDestroy, OnInit } from '@angular/core';
import { ControlValueAccessor, NG_VALIDATORS, NG_VALUE_ACCESSOR, UntypedFormControl, ValidationErrors, Validator, ValidatorFn, Validators } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { ArgumentType, EnumValue, utils, WebappSdkModule, YaSelectOption } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';
import { SelectEnumerationDialogComponent } from '../../select-enumeration-dialog/select-enumeration-dialog.component';

@Component({
  standalone: true,
  selector: 'app-enumeration-argument',
  templateUrl: './enumeration-argument.component.html',
  styleUrls: ['../arguments.css', './enumeration-argument.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => EnumerationArgumentComponent),
    multi: true,
  }, {
    provide: NG_VALIDATORS,
    useExisting: forwardRef(() => EnumerationArgumentComponent),
    multi: true,
  }],
  imports: [
    WebappSdkModule,
  ],
})
export class EnumerationArgumentComponent implements ControlValueAccessor, OnInit, Validator, OnDestroy {

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

  selectOptions$ = new BehaviorSubject<YaSelectOption[]>([]);

  private validators: ValidatorFn[] = [];
  private onChange = (_: string | null) => { };
  private subscriptions: Subscription[] = [];

  constructor(private dialog: MatDialog) {
  }

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

    const selectOptions = [];
    for (const enumValue of this.type.enumValue || []) {
      selectOptions.push({
        id: enumValue.label,
        label: enumValue.label,
      });
    }
    this.selectOptions$.next(selectOptions);

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
    this.formControl.setValue(obj);
  }

  registerOnChange(fn: any) {
    this.onChange = fn;
  }

  registerOnTouched(fn: any) {
  }

  openSelectEnumerationDialog() {
    this.dialog.open(SelectEnumerationDialogComponent, {
      width: '600px',
      data: { type: this.type },
      panelClass: ['no-padding-dialog'],
    }).afterClosed().subscribe((result: EnumValue) => {
      if (result) {
        this.writeValue(result.label);
      }
    });
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
