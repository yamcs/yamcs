import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material';
import { Component, Inject } from '@angular/core';

@Component({
  selector: 'app-show-enum-dialog',
  templateUrl: './ShowEnumDialog.html',
})
export class ShowEnumDialog {

  constructor(
    private dialogRef: MatDialogRef<ShowEnumDialog>,
    @Inject(MAT_DIALOG_DATA) readonly data: any) {
  }

  save() {
    this.dialogRef.close();
  }
}
