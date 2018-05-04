import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material';
import { Component, Inject } from '@angular/core';
import { Validators, FormGroup, FormBuilder } from '@angular/forms';
import { subtractDuration } from '../../shared/utils';
import { BehaviorSubject } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';

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
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    this.form = formBuilder.group({
      start: [null, Validators.pattern(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/)],
      stop: [null, Validators.pattern(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/)],
    });

    this.form.valueChanges.subscribe(value => {
      if (this.form.valid) {
        const url = yamcs.getInstanceClient()!.getPacketsDownloadURL({
          start: value.start,
          stop: value.stop,
          format: 'raw',
        });
        this.downloadURL$.next(url);
      } else {
        this.downloadURL$.next(null);
      }
    });

    if (this.data.start && this.data.stop) {
      this.form.setValue({
        start: this.data.start.toISOString(),
        stop: this.data.stop.toISOString(),
      });
    } else {
      const stop = new Date();
      const start = subtractDuration(stop, 'PT1H');
      this.form.setValue({
        start: start.toISOString(),
        stop: stop.toISOString(),
      });
    }
  }

  closeDialog() {
    this.dialogRef.close();
  }
}
