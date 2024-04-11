import { Component, Inject } from '@angular/core';
import { UntypedFormBuilder, UntypedFormControl, UntypedFormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Clearance, WebappSdkModule } from '@yamcs/webapp-sdk';
import { SignificanceLevelComponent } from '../../../shared/significance-level/significance-level.component';

@Component({
  standalone: true,
  selector: 'app-change-level-dialog',
  templateUrl: './change-level-dialog.component.html',
  styleUrl: './change-level-dialog.component.css',
  imports: [
    WebappSdkModule,
    SignificanceLevelComponent,
  ],
})
export class ChangeLevelDialogComponent {

  form: UntypedFormGroup;

  constructor(
    private dialogRef: MatDialogRef<ChangeLevelDialogComponent>,
    formBuilder: UntypedFormBuilder,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {

    this.form = formBuilder.group({
      'level': new UntypedFormControl(null, [Validators.required]),
    });

    if (data.clearance) {
      const clearance = data.clearance as Clearance;
      this.form.setValue({
        level: clearance.level || 'DISABLED',
      });
    }
  }

  confirm() {
    this.dialogRef.close({
      level: this.form.value['level'] === 'DISABLED' ? undefined : this.form.value['level'],
    });
  }
}
