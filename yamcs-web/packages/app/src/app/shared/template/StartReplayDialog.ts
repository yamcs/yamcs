import { Component, Inject } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material';
import { YamcsService } from '../../core/services/YamcsService';
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

    let initialStart = yamcs.getMissionTime().toISOString();
    let initialStop = '';

    if (this.data) {
      if (this.data.start) {
        initialStart = this.data.start.toISOString();
      }
      if (this.data.stop) {
        initialStop = this.data.stop.toISOString();
      }
    }

    this.form = formBuilder.group({
      name: [generateRandomName(), Validators.required],
      start: [initialStart, [
        Validators.required,
        Validators.pattern(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/)
      ]],
      stop: [initialStop, Validators.pattern(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/)],
    });
  }

  start() {
    const replayConfig: {[key: string]: any} = {
      utcStart: this.form.value.start
    };
    if (this.form.value.stop) {
      replayConfig.utcStop = this.form.value.stop;
    }

    this.dialogRef.close({
      name: this.form.value.name,
      type: 'Archive', // TODO make configurable via AppConfig?
      clientId: [this.yamcs.getClientId()],
      config: JSON.stringify(replayConfig),
    });
  }
}
