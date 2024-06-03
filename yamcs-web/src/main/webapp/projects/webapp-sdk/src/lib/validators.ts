import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

export const requireInteger: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  if (control.value === null || control.value === '') {
    return null;  // don't validate empty values to allow optional controls
  }
  const allowed = !isNaN(control.value) && Number.isInteger(parseFloat(control.value));
  return allowed ? null : { 'notInteger': { value: control.value } };
};

export const requireUnsigned: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const allowed = !isNaN(control.value) && parseFloat(control.value) >= 0;
  return allowed ? null : { 'notUnsigned': { value: control.value } };
};

export const requireFloat: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  if (control.value === null || control.value === '') {
    return null;  // don't validate empty values to allow optional controls
  }
  const allowed = !isNaN(control.value) &&
    (Number.isInteger(parseFloat(control.value)) || control.value % 1 !== 0);
  return allowed ? null : { 'notFloat': { value: control.value } };
};

export const requireHex: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  if (!control.value) {
    return null;  // don't validate empty values to allow optional controls
  }
  const value: string = control.value;
  return /^[a-fA-F0-9]+$/.test(value) ? null : { 'notHex': true };
};

export function minHexLengthValidator(minBytes: number): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    if (isEmptyInputValue(control.value) || !hasValidLength(control.value)) {
      return null;
    }
    const minLength = minBytes * 2;
    return control.value.length < minLength ?
      { 'minhexlength': { 'requiredLength': minLength, 'actualLength': control.value.length } } :
      null;
  };
}

export function maxHexLengthValidator(maxBytes: number): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const maxLength = maxBytes * 2;
    return hasValidLength(control.value) && control.value.length > maxLength ?
      { 'maxhexlength': { 'requiredLength': maxLength, 'actualLength': control.value.length } } :
      null;
  };
}

function isEmptyInputValue(value: any): boolean {
  return value === null || value === undefined ||
    ((typeof value === 'string' || Array.isArray(value)) && value.length === 0);
}

function hasValidLength(value: any): boolean {
  return value !== null && value !== undefined && typeof value.length === 'number';
}
