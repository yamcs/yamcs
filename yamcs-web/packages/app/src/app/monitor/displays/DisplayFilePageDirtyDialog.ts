import { Component } from '@angular/core';
import { MatDialogRef } from '@angular/material';

@Component({
  selector: 'app-display-file-page-dirty-dialog',
  templateUrl: './DisplayFilePageDirtyDialog.html',
})
export class DisplayFilePageDirtyDialog {

  constructor(
    private dialogRef: MatDialogRef<DisplayFilePageDirtyDialog>,
  ) { }

  confirmDiscard() {
    this.dialogRef.close(true);
  }
}
