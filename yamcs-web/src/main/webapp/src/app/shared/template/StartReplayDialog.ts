import { Component, Inject } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { YamcsService } from '../../core/services/YamcsService';
import * as utils from '../../shared/utils';
import { generateRandomName } from '../../shared/utils';

@Component({
  selector: 'app-start-replay-dialog',
  templateUrl: './StartReplayDialog.html',
})
export class StartReplayDialog {

  form: FormGroup;

  constructor(
    private dialogRef: MatDialogRef<StartReplayDialog>,
    formBuilder: FormBuilder,
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
      name: [generateRandomName(), Validators.required],
      start: [utils.printLocalDate(initialStart, 'hhmm'), [
        Validators.required,
      ]],
      stop: [utils.printLocalDate(initialStop, 'hhmm')],
    });
  }

  start() {
    const replayConfig: { [key: string]: any; } = {
      utcStart: utils.toISOString(this.form.value.start),
    };
    if (this.form.value.stop) {
      replayConfig.utcStop = utils.toISOString(this.form.value.stop);
    }

    this.dialogRef.close({
      instance: this.yamcs.getInstance(),
      name: this.form.value.name,
      type: 'Archive', // TODO make configurable?
      config: JSON.stringify(replayConfig),
    });
  }
}
