import { AsyncPipe } from '@angular/common';
import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { AbstractControl, ControlContainer, FormArray, FormArrayName, FormGroupDirective, FormGroupName } from '@angular/forms';
import { BehaviorSubject, Subscription } from 'rxjs';
import { map } from 'rxjs/operators';

const defaultErrors: { [key: string]: any; } = {
  date: () => 'invalid date',
  min: (args: { min: number, actual: number; }) => `the minimum value is ${args.min}`,
  max: (args: { max: number, actual: number; }) => `the maximum value is ${args.max}`,
  notFloat: () => 'invalid float',
  notHex: () => 'invalid hex',
  notInteger: () => 'must be integer',
  notUnsigned: () => 'must be unsigned',
  required: () => 'this field is required',
  minlength: () => 'string too small',
  maxlength: () => 'string too large',
  minhexlength: () => 'hexstring too small',
  maxhexlength: () => 'hexstring too large',
  invalidDimension: () => 'invalid dimension',
  argumentRequired: (args: { name: string; }) => {
    return `argument '${args.name}' is required`;
  },
  parameterRequired: (args: { qualifiedName: string, name: string; }) => {
    return `parameter '${args.qualifiedName}' is required`;
  }
};

@Component({
  standalone: true,
  selector: 'ya-errors',
  templateUrl: './errors.component.html',
  styleUrl: './errors.component.css',
  imports: [
    AsyncPipe
],
})
export class YaErrors implements OnInit, OnDestroy {

  @Input()
  controlName: string;

  public errorMessage$ = new BehaviorSubject<string | null>(null);
  public invalid$ = this.errorMessage$.pipe(
    map(errorMessage => !!errorMessage),
  );

  private controlSubscription: Subscription;

  constructor(private controlContainer: ControlContainer) {
  }

  ngOnInit() {
    let control: AbstractControl<any, any>;
    if (this.controlContainer instanceof FormArrayName) {
      const formArray = this.controlContainer.control;
      const index = Number(this.controlName);
      control = formArray.controls[index];
    } else if (this.controlContainer instanceof FormGroupName) {
      const formGroup = this.controlContainer.control;
      control = formGroup.controls[this.controlName];
    } else if (this.controlContainer instanceof FormGroupDirective) {
      const formGroup = this.controlContainer.control;
      control = formGroup.controls[this.controlName];
    } else {
      throw new Error('Unexpected control container');
    }

    this.controlSubscription = control.valueChanges.subscribe(() => {
      this.validateControl(control);
    });

    // Quick-feedback on missing dimensions.
    if (control instanceof FormArray) {
      this.validateControl(control);
    }
  }

  private validateControl(control: AbstractControl) {
    if (control.errors) {
      for (const key in control.errors) {
        const fn = defaultErrors[key];
        if (!fn) {
          console.warn(`No validation message for key '${key}'`);
          this.errorMessage$.next('invalid');
        } else {
          const args = control.errors[key];
          const errorMessage = fn(args);
          this.errorMessage$.next(errorMessage);
        }
        break;
      }
    } else {
      this.errorMessage$.next(null);
    }
  }

  ngOnDestroy() {
    this.controlSubscription?.unsubscribe();
  }
}
