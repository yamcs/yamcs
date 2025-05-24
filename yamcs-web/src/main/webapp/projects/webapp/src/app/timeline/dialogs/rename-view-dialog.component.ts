import { Component, inject } from '@angular/core';
import { NonNullableFormBuilder, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { WebappSdkModule } from '@yamcs/webapp-sdk';

export interface RenameViewDialogData {
  name: string;
}

@Component({
  selector: 'app-rename-view-dialog',
  templateUrl: './rename-view-dialog.component.html',
  imports: [WebappSdkModule],
})
export class RenameViewDialogComponent {
  private formBuilder = inject(NonNullableFormBuilder);
  readonly data = inject<RenameViewDialogData>(MAT_DIALOG_DATA);
  private dialogRef =
    inject<MatDialogRef<RenameViewDialogComponent, string>>(MatDialogRef);

  form = this.formBuilder.group({
    name: [this.data.name, Validators.required],
  });

  isInvalid() {
    return this.form.invalid || this.form.value.name === this.data.name;
  }

  rename() {
    if (this.isInvalid()) {
      return;
    }
    this.dialogRef.close(this.form.value.name);
  }
}
