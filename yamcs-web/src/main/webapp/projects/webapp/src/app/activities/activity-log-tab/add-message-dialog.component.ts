import { Component, inject } from '@angular/core';
import { NonNullableFormBuilder, Validators } from '@angular/forms';
import { MatDialogRef } from '@angular/material/dialog';
import { WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-add-message-dialog',
  templateUrl: './add-message-dialog.component.html',
  imports: [WebappSdkModule],
})
export class AddMessageDialogComponent {
  private formBuilder = inject(NonNullableFormBuilder);

  form = this.formBuilder.group({
    message: ['', [Validators.required]],
  });

  constructor(private dialogRef: MatDialogRef<AddMessageDialogComponent>) {}

  save() {
    const message = this.form.value.message;
    this.dialogRef.close(message);
  }
}
