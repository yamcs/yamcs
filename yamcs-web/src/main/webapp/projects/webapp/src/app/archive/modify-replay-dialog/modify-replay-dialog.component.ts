import { Component, Inject } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import {
  EditReplayProcessorRequest,
  WebappSdkModule,
  YamcsService,
  utils,
  validators,
} from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-modify-replay-dialog',
  templateUrl: './modify-replay-dialog.component.html',
  imports: [WebappSdkModule],
})
export class ModifyReplayDialogComponent {
  form = new FormGroup(
    {
      start: new FormControl<string | null>(null, Validators.required),
      stop: new FormControl<string | null>(null),
    },
    {
      validators: [validators.dateRangeValidator('start', 'stop')],
    },
  );

  constructor(
    private dialogRef: MatDialogRef<ModifyReplayDialogComponent>,
    private yamcs: YamcsService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    let initialStart = yamcs.getMissionTime();
    let initialStop;

    if (this.data) {
      if (this.data.start) {
        initialStart = this.data.start;
      }
      if (this.data.stop) {
        initialStop = this.data.stop;
      }
    }

    this.form.setValue({
      start: utils.toISOString(initialStart),
      stop: initialStop ? utils.toISOString(initialStop) : null,
    });
  }

  submit() {
    const replayConfig: EditReplayProcessorRequest = {
      start: utils.toISOString(this.form.value.start!),
    };
    if (this.form.value.stop) {
      replayConfig.stop = utils.toISOString(this.form.value.stop);
    }

    this.dialogRef.close(replayConfig);
  }
}
