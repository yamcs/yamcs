import { Component, Inject, OnDestroy } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { BehaviorSubject, Subscription } from 'rxjs';
import { DownloadEventsOptions } from '../client';
import { YamcsService } from '../core/services/YamcsService';
import { Option } from '../shared/forms/Select';
import * as utils from '../shared/utils';

@Component({
  templateUrl: './ExportEventsDialog.html',
})
export class ExportEventsDialog implements OnDestroy {

  delimiterOptions: Option[] = [
    { id: 'COMMA', label: 'Comma' },
    { id: 'SEMICOLON', label: 'Semicolon' },
    { id: 'TAB', label: 'Tab' },
  ];

  private formChangeSubscription: Subscription;

  downloadURL$ = new BehaviorSubject<string | null>(null);

  form = new FormGroup({
    start: new FormControl(null),
    stop: new FormControl(null),
    severity: new FormControl(null, Validators.required),
    q: new FormControl(null),
    source: new FormControl(null),
    delimiter: new FormControl(null, Validators.required),
  });

  constructor(
    private dialogRef: MatDialogRef<ExportEventsDialog>,
    private yamcs: YamcsService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    this.form.setValue({
      start: data.start ? utils.toISOString(data.start) : '',
      stop: data.stop ? utils.toISOString(data.stop) : '',
      q: data.q || '',
      source: data.source || '',
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
      if (this.form.value['q']) {
        dlOptions.q = this.form.value['q'];
      }
      if (this.form.value['source']) {
        const source = this.form.value['source'];
        dlOptions.source = (source !== 'ANY') ? source : null;
      }
      const url = this.yamcs.yamcsClient.getEventsDownloadURL(this.yamcs.instance!, dlOptions);
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
