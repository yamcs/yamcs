import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { NonNullableFormBuilder, Validators } from '@angular/forms';
import { MatDialogRef } from '@angular/material/dialog';
import { WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-set-item-failed-dialog',
  templateUrl: './set-item-failed-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [WebappSdkModule],
})
export class SetItemFailedDialogComponent {
  private dialogRef = inject(MatDialogRef<SetItemFailedDialogComponent>);
  private fb = inject(NonNullableFormBuilder);

  form = this.fb.group({
    failureReason: ['', [Validators.required]],
  });

  submit() {
    this.dialogRef.close({
      failureReason: this.form.value['failureReason'],
    });
  }
}
