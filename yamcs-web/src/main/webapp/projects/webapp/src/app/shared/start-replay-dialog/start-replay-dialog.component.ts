import { Component, Inject } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import {
  WebappSdkModule,
  YamcsService,
  utils,
  validators,
} from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-start-replay-dialog',
  templateUrl: './start-replay-dialog.component.html',
  imports: [WebappSdkModule],
})
export class StartReplayDialogComponent {
  form = new FormGroup(
    {
      name: new FormControl<string | null>(null, Validators.required),
      start: new FormControl<string | null>(null, Validators.required),
      stop: new FormControl<string | null>(null),
    },
    {
      validators: [validators.dateRangeValidator('start', 'stop')],
    },
  );

  constructor(
    private dialogRef: MatDialogRef<StartReplayDialogComponent>,
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
      name: utils.generateRandomName(),
      start: utils.toISOString(initialStart),
      stop: initialStop ? utils.toISOString(initialStop) : null,
    });
  }

  start() {
    const replayConfig: { [key: string]: any } = {
      start: utils.toISOString(this.form.value.start!),
      endAction: 'STOP',
    };
    if (this.form.value.stop) {
      replayConfig.stop = utils.toISOString(this.form.value.stop);
    }

    this.dialogRef.close({
      instance: this.yamcs.instance!,
      name: this.form.value.name,
      type: 'Archive', // TODO make configurable?
      persistent: true,
      config: JSON.stringify(replayConfig),
    });
  }
}
