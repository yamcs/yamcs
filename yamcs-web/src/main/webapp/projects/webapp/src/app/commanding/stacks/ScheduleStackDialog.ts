import { ChangeDetectionStrategy, Component } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { MatDialogRef } from '@angular/material/dialog';

@Component({
  selector: 'app-schedule-stack-dialog',
  templateUrl: './ScheduleStackDialog.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ScheduleStackDialog {

  form: UntypedFormGroup;

  constructor(
    private dialogRef: MatDialogRef<ScheduleStackDialog>,
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
