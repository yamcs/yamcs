import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import { FormControl, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import * as utils from '../../shared/utils';

@Component({
  templateUrl: './JumpToDialog.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JumpToDialog {

  date = new FormControl(null, [
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
