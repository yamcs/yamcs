import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import { AbstractControl, FormBuilder, FormControl, FormGroup, ValidatorFn, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { UserInfo } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';

const PASSWORD_VALIDATOR: ValidatorFn = (control: AbstractControl) => {
  const pw1 = control.get('password')!.value;
  const pw2 = control.get('passwordConfirmation')!.value;
  return pw1 && pw2 && pw1 !== pw2 ? { 'passwordMismatch': true } : null;
};

@Component({
  selector: 'app-change-user-password-dialog',
  templateUrl: './ChangeUserPasswordDialog.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChangeUserPasswordDialog {

  form: FormGroup;
  user: UserInfo;

  constructor(
    @Inject(MAT_DIALOG_DATA) readonly data: any,
    private yamcs: YamcsService,
    formBuilder: FormBuilder,
  ) {
    this.user = data.user;
    this.form = formBuilder.group({
      password: new FormControl(null, Validators.required),
      passwordConfirmation: new FormControl(null, Validators.required),
    }, {
      validator: PASSWORD_VALIDATOR,
    });
  }

  changePassword() {
    const formValue = this.form.value;
    this.yamcs.yamcsClient.editUser(this.user.name, {
      password: formValue.password,
    });
  }
}
