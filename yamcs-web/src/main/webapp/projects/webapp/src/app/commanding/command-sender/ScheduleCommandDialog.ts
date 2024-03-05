import { ChangeDetectionStrategy, Component } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { MatDialogRef } from '@angular/material/dialog';

@Component({
  selector: 'app-schedule-command-dialog',
  templateUrl: './ScheduleCommandDialog.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ScheduleCommandDialog {

  form: UntypedFormGroup;

  constructor(
    private dialogRef: MatDialogRef<ScheduleCommandDialog>,
    formBuilder: UntypedFormBuilder,
  ) {
    this.form = formBuilder.group({
      executionTime: ['', [Validators.required]],
      tags: [[], []],
    });
  }

  schedule() {
    this.dialogRef.close(this.form.value);
  }
}
