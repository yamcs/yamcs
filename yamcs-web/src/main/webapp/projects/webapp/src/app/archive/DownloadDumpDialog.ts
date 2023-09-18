import { Component, Inject } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { YamcsService, utils } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';

@Component({
  selector: 'app-download-dump-dialog',
  templateUrl: './DownloadDumpDialog.html',
})
export class DownloadDumpDialog {

  downloadURL$ = new BehaviorSubject<string | null>(null);

  form: UntypedFormGroup;

  constructor(
    private dialogRef: MatDialogRef<DownloadDumpDialog>,
    private yamcs: YamcsService,
    formBuilder: UntypedFormBuilder,
    @Inject(MAT_DIALOG_DATA) data: any,
  ) {
    this.form = formBuilder.group({
      start: [null, Validators.required],
      stop: [null, Validators.required],
    });

    this.form.valueChanges.subscribe(value => {
      if (this.form.valid) {
        const url = yamcs.yamcsClient.getPacketsDownloadURL(yamcs.instance!, {
          start: utils.toISOString(value.start),
          stop: utils.toISOString(value.stop),
          format: 'raw',
        });
        this.downloadURL$.next(url);
      } else {
        this.downloadURL$.next(null);
      }
    });

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

  closeDialog() {
    this.dialogRef.close();
  }
}
