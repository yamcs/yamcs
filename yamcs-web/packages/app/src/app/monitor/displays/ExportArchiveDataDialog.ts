import { Component, Inject, OnDestroy } from '@angular/core';
import { FormControl, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material';
import { BehaviorSubject, Subscription } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';
import { subtractDuration } from '../../shared/utils';

@Component({
  selector: 'app-export-archive-data-dialog',
  templateUrl: './ExportArchiveDataDialog.html',
})
export class ExportArchiveDataDialog implements OnDestroy {

  private startValueChangeSubscription: Subscription;
  private stopValueChangeSubscription: Subscription;

  downloadURL$ = new BehaviorSubject<string | null>(null);

  start = new FormControl(null, [
    Validators.pattern(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/),
  ]);
  stop = new FormControl(null, [
    Validators.pattern(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/),
  ]);

  constructor(
    private dialogRef: MatDialogRef<ExportArchiveDataDialog>,
    private yamcs: YamcsService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    if (this.data.start && this.data.stop) {
      this.start.setValue(this.data.start.toISOString());
      this.stop.setValue(this.data.stop.toISOString());
    } else {
      const stop = new Date();
      const start = subtractDuration(stop, 'PT1H');
      this.start.setValue(start.toISOString());
      this.stop.setValue(stop.toISOString());
    }

    this.startValueChangeSubscription = this.start.valueChanges.subscribe(() => {
      this.updateURL();
    });
    this.stopValueChangeSubscription = this.stop.valueChanges.subscribe(() => {
      this.updateURL();
    });

    this.updateURL();
  }

  closeDialog() {
    if (this.start.valid && this.stop.valid) {
      this.dialogRef.close(true);
    }
  }

  private updateURL() {
    const url = this.yamcs.getInstanceClient()!.getBatchParameterValuesDownloadURL({
      start: this.start.value,
      stop: this.stop.value,
      parameters: this.data.parameterIds,
      format: 'csv',
    });
    this.downloadURL$.next(url);
  }

  ngOnDestroy() {
    if (this.startValueChangeSubscription) {
      this.startValueChangeSubscription.unsubscribe();
    }
    if (this.stopValueChangeSubscription) {
      this.stopValueChangeSubscription.unsubscribe();
    }
  }
}
