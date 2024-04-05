import { ChangeDetectionStrategy, Component } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { MatDialogRef } from '@angular/material/dialog';
import { WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-schedule-command-dialog',
  templateUrl: './schedule-command-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class ScheduleCommandDialogComponent {

  form: UntypedFormGroup;

  constructor(
    private dialogRef: MatDialogRef<ScheduleCommandDialogComponent>,
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
