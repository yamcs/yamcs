import { Component, Inject, OnDestroy } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { BehaviorSubject, Subscription } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';
import * as utils from '../../shared/utils';
import { subtractDuration } from '../../shared/utils';

@Component({
  selector: 'app-export-archive-data-dialog',
  templateUrl: './ExportArchiveDataDialog.html',
})
export class ExportArchiveDataDialog implements OnDestroy {

  private formChangeSubscription: Subscription;

  downloadURL$ = new BehaviorSubject<string | null>(null);

  form = new FormGroup({
    start: new FormControl(null, Validators.required),
    stop: new FormControl(null, Validators.required),
  });

  constructor(
    private dialogRef: MatDialogRef<ExportArchiveDataDialog>,
    private yamcs: YamcsService,
    @Inject(MAT_DIALOG_DATA) private data: any,
  ) {
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

    this.formChangeSubscription = this.form.valueChanges.subscribe(() => {
      this.updateURL();
    });

    this.updateURL();
  }

  closeDialog() {
    this.dialogRef.close(true);
  }

  private updateURL() {
    if (this.form.valid) {
      const url = this.yamcs.yamcsClient.getParameterValuesDownloadURL(this.yamcs.getInstance().name, {
        start: utils.toISOString(this.form.value['start']),
        stop: utils.toISOString(this.form.value['stop']),
        parameters: this.data.parameterIds,
      });
      this.downloadURL$.next(url);
    } else {
      this.downloadURL$.next(null);
    }
  }

  ngOnDestroy() {
    if (this.formChangeSubscription) {
      this.formChangeSubscription.unsubscribe();
    }
  }
}
