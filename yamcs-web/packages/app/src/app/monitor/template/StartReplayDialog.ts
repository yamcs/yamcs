import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material';
import { Component, Inject } from '@angular/core';
import { FormGroup, FormBuilder, Validators } from '@angular/forms';
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
    this.form = formBuilder.group({
      name: [generateRandomName(), Validators.required],
      start: [yamcs.getMissionTime().toISOString(), [
        Validators.required,
        Validators.pattern(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/)
      ]],
      stop: ['', Validators.pattern(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/)],
    });
  }

  start() {
    const replayConfig: {[key: string]: any} = {
      utcStart: this.form.value.start
    };
    if (this.form.value.stop) {
      replayConfig.utcStop = this.form.value.stop;
    }

    this.yamcs.getInstanceClient()!.createProcessor({
      name: this.form.value.name,
      type: 'Archive', // TODO make configurable via AppConfig?
      clientId: [this.yamcs.getClientId()],
      config: JSON.stringify(replayConfig),
    }).then(() => this.dialogRef.close());
  }
}
