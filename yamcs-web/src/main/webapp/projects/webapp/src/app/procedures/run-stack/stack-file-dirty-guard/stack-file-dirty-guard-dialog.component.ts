import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-stack-file-page-dirty-dialog',
  templateUrl: './stack-file-dirty-guard-dialog.component.html',
  imports: [WebappSdkModule],
})
export class StackFilePageDirtyDialog {
  constructor(
    private dialogRef: MatDialogRef<StackFilePageDirtyDialog>,
    @Inject(MAT_DIALOG_DATA) private data: any,
  ) {}

  confirmDiscard() {
    const { stackFileService } = this.data;
    stackFileService.dirty$.next(false);
    this.dialogRef.close(true);
  }
}
