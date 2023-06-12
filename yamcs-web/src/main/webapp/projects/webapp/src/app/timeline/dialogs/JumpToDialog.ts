import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import { UntypedFormControl, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { utils } from '@yamcs/webapp-sdk';

@Component({
  templateUrl: './JumpToDialog.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JumpToDialog {

  date = new UntypedFormControl(null, [
    Validators.required,
  ]);

  constructor(
    private dialogRef: MatDialogRef<JumpToDialog>,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    if (this.data.date) {
      this.date.setValue(utils.toISOString(this.data.date));
    }
  }

  select() {
    const date = utils.toDate(this.date.value);
    this.dialogRef.close({ date });
  }
}
