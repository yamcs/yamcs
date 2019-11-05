import { Component, Inject } from '@angular/core';
import { FormControl, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import * as utils from '../shared/utils';

@Component({
  selector: 'app-jump-to-dialog',
  templateUrl: './JumpToDialog.html',
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
      this.date.setValue(utils.printLocalDate(this.data.date, 'hhmm'));
    }
  }

  select() {
    const date = utils.toDate(this.date.value);
    this.dialogRef.close({ date });
  }
}
