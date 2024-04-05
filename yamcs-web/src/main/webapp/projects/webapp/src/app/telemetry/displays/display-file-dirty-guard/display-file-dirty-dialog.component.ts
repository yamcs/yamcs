import { Component } from '@angular/core';
import { MatDialogRef } from '@angular/material/dialog';
import { WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-display-file-page-dirty-dialog',
  templateUrl: './display-file-dirty-dialog.component.html',
  imports: [
    WebappSdkModule,
  ],
})
export class DisplayFilePageDirtyDialogComponent {

  constructor(
    private dialogRef: MatDialogRef<DisplayFilePageDirtyDialogComponent>,
  ) { }

  confirmDiscard() {
    this.dialogRef.close(true);
  }
}
