import { Component, Inject } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { YamcsService } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-set-failed-dialog',
  templateUrl: './SetFailedDialog.html',
})
export class SetFailedDialog {

  form: UntypedFormGroup;

  constructor(
    private dialogRef: MatDialogRef<SetFailedDialog>,
    formBuilder: UntypedFormBuilder,
    yamcs: YamcsService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    this.form = formBuilder.group({
      failureReason: ['', [Validators.required]],
    });
  }

  async submit() {
    this.dialogRef.close({
      failureReason: this.form.value["failureReason"],
    });
  }
}
