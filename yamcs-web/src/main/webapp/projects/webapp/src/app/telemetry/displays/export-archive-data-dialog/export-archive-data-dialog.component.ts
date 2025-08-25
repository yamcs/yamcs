import { Component, Inject, OnDestroy } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import {
  DownloadParameterValuesOptions,
  WebappSdkModule,
  YaSelectOption,
  YamcsService,
  utils,
  validators,
} from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';

@Component({
  selector: 'app-export-archive-data-dialog',
  templateUrl: './export-archive-data-dialog.component.html',
  imports: [WebappSdkModule],
})
export class ExportArchiveDataDialogComponent implements OnDestroy {
  delimiterOptions: YaSelectOption[] = [
    { id: 'COMMA', label: 'Comma' },
    { id: 'SEMICOLON', label: 'Semicolon' },
    { id: 'TAB', label: 'Tab' },
  ];

  headerOptions: YaSelectOption[] = [
    { id: 'QUALIFIED_NAME', label: 'Qualified names' },
    { id: 'SHORT_NAME', label: 'Short names' },
    { id: 'NONE', label: 'None' },
  ];

  private formChangeSubscription: Subscription;

  downloadURL$ = new BehaviorSubject<string | null>(null);

  form = new FormGroup(
    {
      start: new FormControl<string | null>(null),
      stop: new FormControl<string | null>(null),
      delimiter: new FormControl<string | null>(null, Validators.required),
      interval: new FormControl<number | null>(null),
      header: new FormControl<string | null>(null, Validators.required),
    },
    {
      validators: [validators.dateRangeValidator('start', 'stop')],
    },
  );

  constructor(
    private dialogRef: MatDialogRef<ExportArchiveDataDialogComponent>,
    private yamcs: YamcsService,
    @Inject(MAT_DIALOG_DATA) private data: any,
  ) {
    let start = data.start;
    let stop = data.stop;
    if (!start || !stop) {
      stop = yamcs.getMissionTime();
      start = utils.subtractDuration(stop, 'PT1H');
    }

    this.form.setValue({
      start: utils.toISOString(start),
      stop: utils.toISOString(stop),
      delimiter: 'TAB',
      header: 'QUALIFIED_NAME',
      interval: null,
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
      const dlOptions: DownloadParameterValuesOptions = {
        delimiter: this.form.value.delimiter as any,
        header: this.form.value.header as any,
      };
      if (this.data.parameterIds) {
        dlOptions.parameters = this.data.parameterIds;
      }
      if (this.data.list) {
        dlOptions.list = this.data.list;
      }
      if (this.form.value.start) {
        dlOptions.start = utils.toISOString(this.form.value.start);
      }
      if (this.form.value.stop) {
        dlOptions.stop = utils.toISOString(this.form.value.stop);
      }
      if (this.form.value.interval) {
        dlOptions.interval = this.form.value.interval;
      }
      if (this.data.filename) {
        dlOptions.filename = this.data.filename;
      }
      const url = this.yamcs.yamcsClient.getParameterValuesDownloadURL(
        this.yamcs.instance!,
        dlOptions,
      );
      this.downloadURL$.next(url);
    } else {
      this.downloadURL$.next(null);
    }
  }

  ngOnDestroy() {
    this.formChangeSubscription?.unsubscribe();
  }
}
