import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material';
import { Component, Inject } from '@angular/core';
import { FormControl, Validators } from '@angular/forms';
import { subtractDuration } from '../../shared/utils';

@Component({
  selector: 'app-select-range-dialog',
  templateUrl: './SelectRangeDialog.html',
})
export class SelectRangeDialog {

  start = new FormControl(null, [
    Validators.pattern(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/),
  ]);
  stop = new FormControl(null, [
    Validators.pattern(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/),
  ]);

  constructor(
    private dialogRef: MatDialogRef<SelectRangeDialog>,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    if (this.data.start && this.data.stop) {
      this.start.setValue(this.data.start.toISOString());
      this.stop.setValue(this.data.stop.toISOString());
    } else {
      const stop = new Date();
      const start = subtractDuration(stop, 'PT1H');
      this.start.setValue(start.toISOString());
      this.stop.setValue(stop.toISOString());
    }
  }

  select() {
    const start = new Date(Date.parse(this.start.value));
    const stop = new Date(Date.parse(this.stop.value));
    this.dialogRef.close({
      start, stop
    });
  }
}
