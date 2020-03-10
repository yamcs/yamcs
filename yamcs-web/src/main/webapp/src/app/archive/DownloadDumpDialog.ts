import { Component, Inject } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { BehaviorSubject } from 'rxjs';
import { YamcsService } from '../core/services/YamcsService';
import * as utils from '../shared/utils';
import { subtractDuration } from '../shared/utils';

@Component({
  selector: 'app-download-dump-dialog',
  templateUrl: './DownloadDumpDialog.html',
})
export class DownloadDumpDialog {

  downloadURL$ = new BehaviorSubject<string | null>(null);

  form: FormGroup;

  constructor(
    private dialogRef: MatDialogRef<DownloadDumpDialog>,
    yamcs: YamcsService,
    formBuilder: FormBuilder,
    @Inject(MAT_DIALOG_DATA) data: any,
  ) {
    this.form = formBuilder.group({
      start: [null, Validators.required],
      stop: [null, Validators.required],
    });

    this.form.valueChanges.subscribe(value => {
      if (this.form.valid) {
        const url = yamcs.yamcsClient.getPacketsDownloadURL(yamcs.getInstance(), {
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
      stop = new Date();
      start = subtractDuration(stop, 'PT1H');
    }
    this.form.setValue({
      start: utils.printLocalDate(start, 'hhmm'),
      stop: utils.printLocalDate(stop, 'hhmm'),
    });
  }

  closeDialog() {
    this.dialogRef.close();
  }
}
