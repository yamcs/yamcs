import { Component } from '@angular/core';
import { MatLegacyDialogRef } from '@angular/material/legacy-dialog';

@Component({
  selector: 'app-display-file-page-dirty-dialog',
  templateUrl: './DisplayFilePageDirtyDialog.html',
})
export class DisplayFilePageDirtyDialog {

  constructor(
    private dialogRef: MatLegacyDialogRef<DisplayFilePageDirtyDialog>,
  ) { }

  confirmDiscard() {
    this.dialogRef.close(true);
  }
}
