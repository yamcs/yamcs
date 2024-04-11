import { Component } from '@angular/core';
import { MatDialogRef } from '@angular/material/dialog';
import { WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-stack-file-page-dirty-dialog',
  templateUrl: './stack-file-dirty-guard-dialog.component.html',
  imports: [
    WebappSdkModule,
  ],
})
export class StackFilePageDirtyDialog {

  constructor(
    private dialogRef: MatDialogRef<StackFilePageDirtyDialog>,
  ) { }

  confirmDiscard() {
    this.dialogRef.close(true);
  }
}
