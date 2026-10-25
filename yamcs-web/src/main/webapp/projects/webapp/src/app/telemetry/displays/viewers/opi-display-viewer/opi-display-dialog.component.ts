import { Component, Inject, signal } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { WebappSdkModule } from '@yamcs/webapp-sdk';

export interface OpiDisplayDialogData {
  kind: 'confirm' | 'password';
  title: string;
  message: string;
  password?: string;
}

/**
 * Non-blocking dialog requested by a script of a sandboxed OPI display.
 * Closes with true when confirmed, or when the expected password was entered.
 */
@Component({
  selector: 'app-opi-display-dialog',
  templateUrl: './opi-display-dialog.component.html',
  styleUrl: './opi-display-dialog.component.css',
  imports: [WebappSdkModule],
})
export class OpiDisplayDialogComponent {
  form: UntypedFormGroup;
  wrongPassword = signal(false);

  constructor(
    private dialogRef: MatDialogRef<OpiDisplayDialogComponent, boolean>,
    formBuilder: UntypedFormBuilder,
    @Inject(MAT_DIALOG_DATA) readonly data: OpiDisplayDialogData,
  ) {
    this.form = formBuilder.group({ password: [''] });
  }

  confirm() {
    if (this.data.kind === 'password') {
      // Like Yamcs Studio, the dialog stays open until the expected
      // password is entered, or the user cancels.
      if (this.form.value['password'] !== this.data.password) {
        this.wrongPassword.set(true);
        return;
      }
    }
    this.dialogRef.close(true);
  }
}
