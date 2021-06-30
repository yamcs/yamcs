import { Component, Host, Input, OnDestroy, OnInit, Optional } from '@angular/core';
import { FormGroupDirective } from '@angular/forms';
import { BehaviorSubject, Subscription } from 'rxjs';
import { map } from 'rxjs/operators';

const defaultErrors: { [key: string]: any; } = {
  date: () => 'invalid date',
  min: (args: { min: number, actual: number; }) => `the minimum value is ${args.min}`,
  max: (args: { max: number, actual: number; }) => `the maximum value is ${args.max}`,
  notFloat: () => 'invalid float',
  notHex: () => 'invalid hex',
  notInteger: () => 'must be integer',
  required: () => 'this field is required',
  minlength: () => 'string too small',
  maxlength: () => 'string too large',
};

@Component({
  selector: 'app-errors',
  templateUrl: './Errors.html',
  styleUrls: ['./Errors.css'],
})
export class Errors implements OnInit, OnDestroy {

  private controlSubscription: Subscription;

  @Input()
  controlName: string;

  public errorMessage$ = new BehaviorSubject<string | null>(null);
  public invalid$ = this.errorMessage$.pipe(
    map(errorMessage => !!errorMessage),
  );

  constructor(@Optional() @Host() private formGroupDirective: FormGroupDirective) {
  }

  ngOnInit() {
    const formGroup = this.formGroupDirective.control;
    const control = formGroup.controls[this.controlName];
    this.controlSubscription = control.valueChanges.subscribe(() => {
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
    });
  }

  ngOnDestroy() {
    if (this.controlSubscription) {
      this.controlSubscription.unsubscribe();
    }
  }
}
