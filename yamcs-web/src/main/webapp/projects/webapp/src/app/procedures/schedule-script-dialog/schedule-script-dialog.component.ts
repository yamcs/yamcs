import { ChangeDetectionStrategy, Component } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { MatDialogRef } from '@angular/material/dialog';
import { SharedModule } from '../../shared/SharedModule';

@Component({
  standalone: true,
  selector: 'app-schedule-script-dialog',
  templateUrl: './schedule-script-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    SharedModule,
  ],
})
export class ScheduleScriptDialogComponent {

  form: UntypedFormGroup;

  constructor(
    private dialogRef: MatDialogRef<ScheduleScriptDialogComponent>,
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
