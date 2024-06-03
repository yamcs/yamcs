import { Component, Inject } from '@angular/core';
import { UntypedFormControl, UntypedFormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { WebappSdkModule, YamcsService, utils } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-select-range-dialog',
  templateUrl: './select-range-dialog.component.html',
  imports: [
    WebappSdkModule,
  ],
})
export class SelectRangeDialogComponent {

  form = new UntypedFormGroup({
    start: new UntypedFormControl(null, Validators.required),
    stop: new UntypedFormControl(null, Validators.required),
  });

  constructor(
    private dialogRef: MatDialogRef<SelectRangeDialogComponent>,
    @Inject(MAT_DIALOG_DATA) data: any,
    private yamcs: YamcsService,
  ) {
    let start = data.start;
    let stop = data.stop;
    if (!start || !stop) {
      stop = this.yamcs.getMissionTime();
      start = utils.subtractDuration(stop, 'PT1H');
    }
    this.form.setValue({
      start: utils.toISOString(start),
      stop: utils.toISOString(stop),
    });
  }

  select() {
    const start = utils.toDate(this.form.value['start']);
    const stop = utils.toDate(this.form.value['stop']);
    this.dialogRef.close({ start, stop });
  }
}
