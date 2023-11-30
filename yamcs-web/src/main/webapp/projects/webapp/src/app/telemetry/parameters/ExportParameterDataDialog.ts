import { ChangeDetectionStrategy, Component, Inject, OnDestroy } from '@angular/core';
import { FormControl, UntypedFormControl, UntypedFormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { DownloadParameterValuesOptions, SelectOption, YamcsService, utils } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';

@Component({
  templateUrl: './ExportParameterDataDialog.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExportParameterDataDialog implements OnDestroy {

  delimiterOptions: SelectOption[] = [
    { id: 'COMMA', label: 'Comma' },
    { id: 'SEMICOLON', label: 'Semicolon' },
    { id: 'TAB', label: 'Tab' },
  ];

  private formChangeSubscription: Subscription;

  downloadURL$ = new BehaviorSubject<string | null>(null);

  form = new UntypedFormGroup({
    start: new UntypedFormControl(null),
    stop: new UntypedFormControl(null),
    delimiter: new UntypedFormControl(null, Validators.required),
    interval: new FormControl<number | null>(null),
  });

  constructor(
    private dialogRef: MatDialogRef<ExportParameterDataDialog>,
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
      start: data.start ? utils.toISOString(data.start) : '',
      stop: data.stop ? utils.toISOString(data.stop) : '',
      delimiter: 'TAB',
      interval: '',
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
        parameters: this.data.parameter,
        delimiter: this.form.value['delimiter'],
      };
      if (this.form.value['start']) {
        dlOptions.start = utils.toISOString(this.form.value['start']);
      }
      if (this.form.value['stop']) {
        dlOptions.stop = utils.toISOString(this.form.value['stop']);
      }
      if (this.form.value['interval']) {
        dlOptions.interval = this.form.value['interval'];
      }
      const url = this.yamcs.yamcsClient.getParameterValuesDownloadURL(this.yamcs.instance!, dlOptions);
      this.downloadURL$.next(url);
    } else {
      this.downloadURL$.next(null);
    }
  }

  ngOnDestroy() {
    this.formChangeSubscription?.unsubscribe();
  }
}
