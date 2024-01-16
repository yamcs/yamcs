import { ChangeDetectionStrategy, Component } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { MatDialogRef } from '@angular/material/dialog';

@Component({
  selector: 'app-schedule-script-dialog',
  templateUrl: './ScheduleScriptDialog.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ScheduleScriptDialog {

  form: UntypedFormGroup;

  constructor(
    private dialogRef: MatDialogRef<ScheduleScriptDialog>,
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
