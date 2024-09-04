import { Component, Inject, OnDestroy } from '@angular/core';
import { UntypedFormControl, UntypedFormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { DownloadEventsOptions, WebappSdkModule, YamcsService, utils } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';

@Component({
  standalone: true,
  templateUrl: './export-events-dialog.component.html',
  imports: [
    WebappSdkModule,
  ],
})
export class ExportEventsDialogComponent implements OnDestroy {

  private formChangeSubscription: Subscription;

  downloadURL$ = new BehaviorSubject<string | null>(null);

  form = new UntypedFormGroup({
    start: new UntypedFormControl(null),
    stop: new UntypedFormControl(null),
    severity: new UntypedFormControl(null, Validators.required),
    filter: new UntypedFormControl(null),
    source: new UntypedFormControl([]),
    delimiter: new UntypedFormControl(null, Validators.required),
  });

  constructor(
    private dialogRef: MatDialogRef<ExportEventsDialogComponent>,
    private yamcs: YamcsService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    this.form.setValue({
      start: data.start ? utils.toISOString(data.start) : '',
      stop: data.stop ? utils.toISOString(data.stop) : '',
      filter: data.filter || '',
      source: data.source,
      severity: data.severity,
      delimiter: 'TAB',
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
      const dlOptions: DownloadEventsOptions = {
        delimiter: this.form.value['delimiter'],
        severity: this.form.value['severity'],
      };
      if (this.form.value['start']) {
        dlOptions.start = utils.toISOString(this.form.value['start']);
      }
      if (this.form.value['stop']) {
        dlOptions.stop = utils.toISOString(this.form.value['stop']);
      }
      if (this.form.value['filter']) {
        dlOptions.filter = this.form.value['filter'];
      }
      if (this.form.value['source'].length) {
        const source = this.form.value['source'];
        dlOptions.source = source;
      }
      const url = this.yamcs.yamcsClient.getEventsDownloadURL(this.yamcs.instance!, dlOptions);
      this.downloadURL$.next(url);
    } else {
      this.downloadURL$.next(null);
    }
  }

  ngOnDestroy() {
    this.formChangeSubscription?.unsubscribe();
  }
}
