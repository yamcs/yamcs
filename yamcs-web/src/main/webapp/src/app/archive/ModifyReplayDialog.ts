import { Component, Inject } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { MatLegacyDialogRef, MAT_LEGACY_DIALOG_DATA } from '@angular/material/legacy-dialog';
import { EditReplayProcessorRequest } from '../client';
import { YamcsService } from '../core/services/YamcsService';
import * as utils from '../shared/utils';

@Component({
  selector: 'app-modify-replay-dialog',
  templateUrl: './ModifyReplayDialog.html',
})
export class ModifyReplayDialog {

  form: UntypedFormGroup;

  constructor(
    private dialogRef: MatLegacyDialogRef<ModifyReplayDialog>,
    formBuilder: UntypedFormBuilder,
    private yamcs: YamcsService,
    @Inject(MAT_LEGACY_DIALOG_DATA) readonly data: any,
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
      start: [utils.toISOString(initialStart), [
        Validators.required,
      ]],
      stop: [initialStop ? utils.toISOString(initialStop) : ''],
    });
  }

  submit() {
    const replayConfig: EditReplayProcessorRequest = {
      start: utils.toISOString(this.form.value.start),
    };
    if (this.form.value.stop) {
      replayConfig.stop = utils.toISOString(this.form.value.stop);
    }

    this.dialogRef.close(replayConfig);
  }
}
