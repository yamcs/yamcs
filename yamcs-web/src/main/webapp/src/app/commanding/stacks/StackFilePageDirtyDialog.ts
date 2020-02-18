import { Component } from '@angular/core';
import { MatDialogRef } from '@angular/material/dialog';

@Component({
  selector: 'app-stack-file-page-dirty-dialog',
  templateUrl: './StackFilePageDirtyDialog.html',
})
export class StackFilePageDirtyDialog {

  constructor(
    private dialogRef: MatDialogRef<StackFilePageDirtyDialog>,
  ) { }

  confirmDiscard() {
    this.dialogRef.close(true);
  }
}
