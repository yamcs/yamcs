import {
  ChangeDetectionStrategy,
  Component,
  Inject,
  OnDestroy,
} from '@angular/core';
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
  templateUrl: './export-parameter-data-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [WebappSdkModule],
})
export class ExportParameterDataDialogComponent implements OnDestroy {
  delimiterOptions: YaSelectOption[] = [
    { id: 'COMMA', label: 'Comma' },
    { id: 'SEMICOLON', label: 'Semicolon' },
    { id: 'TAB', label: 'Tab' },
  ];

  private formChangeSubscription: Subscription;

  downloadURL$ = new BehaviorSubject<string | null>(null);

  form = new FormGroup(
    {
      start: new FormControl<string | null>(null),
      stop: new FormControl<string | null>(null),
      delimiter: new FormControl<string | null>(null, Validators.required),
      interval: new FormControl<number | null>(null),
    },
    {
      validators: [validators.dateRangeValidator('start', 'stop')],
    },
  );

  constructor(
    private dialogRef: MatDialogRef<ExportParameterDataDialogComponent>,
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
        parameters: this.data.parameter,
        delimiter: this.form.value.delimiter as any,
      };
      if (this.form.value.start) {
        dlOptions.start = utils.toISOString(this.form.value.start);
      }
      if (this.form.value.stop) {
        dlOptions.stop = utils.toISOString(this.form.value.stop);
      }
      if (this.form.value.interval) {
        dlOptions.interval = this.form.value.interval;
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
