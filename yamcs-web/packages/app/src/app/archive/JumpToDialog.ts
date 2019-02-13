import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material';
import { Component, Inject } from '@angular/core';
import { FormControl, Validators } from '@angular/forms';

@Component({
  selector: 'app-jump-to-dialog',
  templateUrl: './JumpToDialog.html',
})
export class JumpToDialog {

  date = new FormControl(null, [
    Validators.required,
    Validators.pattern(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/),
  ]);

  constructor(
    private dialogRef: MatDialogRef<JumpToDialog>,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    if (this.data.date) {
      this.date.setValue(this.data.date.toISOString());
    }
  }

  select() {
    const date = new Date(Date.parse(this.date.value));
    this.dialogRef.close({ date });
  }
}
