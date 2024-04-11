import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import { AbstractControl, UntypedFormBuilder, UntypedFormControl, UntypedFormGroup, ValidatorFn, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { UserInfo, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';

const PASSWORD_VALIDATOR: ValidatorFn = (control: AbstractControl) => {
  const pw1 = control.get('password')!.value;
  const pw2 = control.get('passwordConfirmation')!.value;
  return pw1 && pw2 && pw1 !== pw2 ? { 'passwordMismatch': true } : null;
};

@Component({
  standalone: true,
  selector: 'app-change-user-password-dialog',
  templateUrl: './change-user-password-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class ChangeUserPasswordDialogComponent {

  form: UntypedFormGroup;
  user: UserInfo;

  constructor(
    @Inject(MAT_DIALOG_DATA) readonly data: any,
    private yamcs: YamcsService,
    formBuilder: UntypedFormBuilder,
  ) {
    this.user = data.user;
    this.form = formBuilder.group({
      password: new UntypedFormControl(null, Validators.required),
      passwordConfirmation: new UntypedFormControl(null, Validators.required),
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
