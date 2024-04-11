import { Component, Inject } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { WebappSdkModule, YamcsService, utils } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-start-replay-dialog',
  templateUrl: './start-replay-dialog.component.html',
  imports: [
    WebappSdkModule,
  ],
})
export class StartReplayDialogComponent {

  form: UntypedFormGroup;

  constructor(
    private dialogRef: MatDialogRef<StartReplayDialogComponent>,
    formBuilder: UntypedFormBuilder,
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

    this.form = formBuilder.group({
      name: [utils.generateRandomName(), Validators.required],
      start: [utils.toISOString(initialStart), [
        Validators.required,
      ]],
      stop: [initialStop ? utils.toISOString(initialStop) : ''],
    });
  }

  start() {
    const replayConfig: { [key: string]: any; } = {
      start: utils.toISOString(this.form.value.start),
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
