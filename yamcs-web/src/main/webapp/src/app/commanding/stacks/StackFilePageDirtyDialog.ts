import { Component } from '@angular/core';
import { MatLegacyDialogRef } from '@angular/material/legacy-dialog';

@Component({
  selector: 'app-stack-file-page-dirty-dialog',
  templateUrl: './StackFilePageDirtyDialog.html',
})
export class StackFilePageDirtyDialog {

  constructor(
    private dialogRef: MatLegacyDialogRef<StackFilePageDirtyDialog>,
  ) { }

  confirmDiscard() {
    this.dialogRef.close(true);
  }
}
