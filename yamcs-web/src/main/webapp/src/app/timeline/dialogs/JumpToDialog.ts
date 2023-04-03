import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import { UntypedFormControl, Validators } from '@angular/forms';
import { MatLegacyDialogRef, MAT_LEGACY_DIALOG_DATA } from '@angular/material/legacy-dialog';
import * as utils from '../../shared/utils';

@Component({
  templateUrl: './JumpToDialog.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JumpToDialog {

  date = new UntypedFormControl(null, [
    Validators.required,
  ]);

  constructor(
    private dialogRef: MatLegacyDialogRef<JumpToDialog>,
    @Inject(MAT_LEGACY_DIALOG_DATA) readonly data: any,
  ) {
    if (this.data.date) {
      this.date.setValue(utils.toISOString(this.data.date));
    }
  }

  select() {
    const date = utils.toDate(this.date.value);
    this.dialogRef.close({ date });
  }
}
