import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { NonNullableFormBuilder, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { WebappSdkModule } from '@yamcs/webapp-sdk';

export interface ScheduleActivityDialogData {
  type: string;
  name: string;
}

export interface ScheduleActivityDialogResult {
  name: string;
  start: string;
  autoStart: boolean;
  duration: string;
  tags: string[];
}

@Component({
  selector: 'app-schedule-activity-dialog',
  templateUrl: './schedule-activity-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [WebappSdkModule],
})
export class ScheduleActivityDialogComponent {
  private dialogRef: MatDialogRef<
    ScheduleActivityDialogComponent,
    ScheduleActivityDialogResult
  > = inject(MatDialogRef);
  private fb = inject(NonNullableFormBuilder);
  readonly data = inject<ScheduleActivityDialogData>(MAT_DIALOG_DATA);

  form = this.fb.group({
    name: [this.data.name || '', Validators.required],
    start: ['', Validators.required],
    autoStart: [true, Validators.required],
    tags: [[] as string[]],
  });

  schedule() {
    const fv = this.form.getRawValue();
    this.dialogRef.close({
      ...fv,
      duration: '0s',
    });
  }
}
