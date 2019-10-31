import { Component, Inject } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import * as utils from '../../shared/utils';
import { subtractDuration } from '../../shared/utils';

@Component({
  selector: 'app-select-range-dialog',
  templateUrl: './SelectRangeDialog.html',
})
export class SelectRangeDialog {

  form = new FormGroup({
    start: new FormControl(null, Validators.required),
    stop: new FormControl(null, Validators.required),
  });

  constructor(
    private dialogRef: MatDialogRef<SelectRangeDialog>,
    @Inject(MAT_DIALOG_DATA) data: any,
  ) {
    let start = data.start;
    let stop = data.stop;
    if (!start || !stop) {
      stop = new Date();
      start = subtractDuration(stop, 'PT1H');
    }
    this.form.setValue({
      start: utils.printLocalDate(start, 'hhmm'),
      stop: utils.printLocalDate(stop, 'hhmm'),
    });
  }

  select() {
    const start = utils.toDate(this.form.value['start']);
    const stop = utils.toDate(this.form.value['stop']);
    this.dialogRef.close({ start, stop });
  }
}
