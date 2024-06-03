import { Component, Inject } from '@angular/core';
import { UntypedFormControl, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { WebappSdkModule, utils } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-jump-to-dialog',
  templateUrl: './jump-to-dialog.component.html',
  imports: [
    WebappSdkModule,
  ],
})
export class JumpToDialogComponent {

  date = new UntypedFormControl(null, [Validators.required]);

  constructor(
    private dialogRef: MatDialogRef<JumpToDialogComponent>,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    if (this.data.date) {
      this.date.setValue(utils.toISOString(this.data.date));
    }
  }

  select() {
    const date = utils.toDate(this.date.value);
    this.dialogRef.close({ date });
  }
}
