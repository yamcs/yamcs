import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { WebappSdkModule } from '@yamcs/webapp-sdk';

export interface AxisRangeDialogData {
  minimum: number;
  maximum: number;
}

function axisRangeValidator(group: AbstractControl): ValidationErrors | null {
  const { minimum, maximum } = group.value;
  if (minimum !== null && maximum !== null && minimum >= maximum) {
    return { axisRange: true };
  }
  return null;
}

@Component({
  selector: 'app-axis-range-dialog',
  templateUrl: './axis-range-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [WebappSdkModule],
})
export class AxisRangeDialogComponent {
  form = new FormGroup(
    {
      minimum: new FormControl<number | null>(null, Validators.required),
      maximum: new FormControl<number | null>(null, Validators.required),
    },
    { validators: [axisRangeValidator] },
  );

  constructor(
    private dialogRef: MatDialogRef<AxisRangeDialogComponent>,
    @Inject(MAT_DIALOG_DATA) data: Partial<AxisRangeDialogData>,
  ) {
    this.form.setValue({
      minimum: data.minimum ?? null,
      maximum: data.maximum ?? null,
    });
  }

  apply() {
    if (this.form.valid) {
      const result: AxisRangeDialogData = {
        minimum: this.form.value.minimum!,
        maximum: this.form.value.maximum!,
      };
      this.dialogRef.close(result);
    }
  }
}
