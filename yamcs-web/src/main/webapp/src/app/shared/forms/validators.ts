import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

export const requireInteger: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const allowed = !isNaN(control.value) && Number.isInteger(parseFloat(control.value));
  return allowed ? null : { 'notInteger': { value: control.value } };
};

export const requireUnsigned: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const allowed = !isNaN(control.value) && parseFloat(control.value) >= 0;
  return allowed ? null : { 'notUnsigned': { value: control.value } };
};

export const requireFloat: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const allowed = !isNaN(control.value) &&
    (Number.isInteger(parseFloat(control.value)) || control.value % 1 !== 0);
  return allowed ? null : { 'notFloat': { value: control.value } };
};
