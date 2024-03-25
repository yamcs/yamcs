import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { SharedModule } from '../../shared/SharedModule';

@Component({
  standalone: true,
  selector: 'app-set-failed-dialog',
  templateUrl: './set-failed-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    SharedModule,
  ],
})
export class SetFailedDialogComponent {

  form: UntypedFormGroup;

  constructor(
    private dialogRef: MatDialogRef<SetFailedDialogComponent>,
    formBuilder: UntypedFormBuilder,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    this.form = formBuilder.group({
      failureReason: ['', [Validators.required]],
    });
  }

  async submit() {
    this.dialogRef.close({
      failureReason: this.form.value["failureReason"],
    });
  }
}
