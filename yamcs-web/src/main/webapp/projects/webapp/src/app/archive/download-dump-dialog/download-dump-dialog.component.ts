import { Component, Inject } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import {
  WebappSdkModule,
  YamcsService,
  utils,
  validators,
} from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';

@Component({
  selector: 'app-download-dump-dialog',
  templateUrl: './download-dump-dialog.component.html',
  imports: [WebappSdkModule],
})
export class DownloadDumpDialogComponent {
  downloadURL$ = new BehaviorSubject<string | null>(null);

  form = new FormGroup(
    {
      start: new FormControl<string | null>(null, Validators.required),
      stop: new FormControl<string | null>(null, Validators.required),
    },
    {
      validators: [validators.dateRangeValidator('start', 'stop')],
    },
  );

  constructor(
    private dialogRef: MatDialogRef<DownloadDumpDialogComponent>,
    private yamcs: YamcsService,
    @Inject(MAT_DIALOG_DATA) data: any,
  ) {
    this.form.valueChanges.subscribe((value) => {
      if (this.form.valid) {
        const url = yamcs.yamcsClient.getPacketsDownloadURL(yamcs.instance!, {
          start: utils.toISOString(value.start!),
          stop: utils.toISOString(value.stop!),
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
