import { ChangeDetectionStrategy, Component, Inject, OnDestroy } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { BehaviorSubject, Subscription } from 'rxjs';
import { DownloadParameterValuesOptions } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';
import { Option } from '../../shared/forms/Select';
import * as utils from '../../shared/utils';
import { subtractDuration } from '../../shared/utils';

@Component({
  templateUrl: './ExportParameterDataDialog.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExportParameterDataDialog implements OnDestroy {

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
    delimiter: new FormControl(null, Validators.required),
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
      start = subtractDuration(stop, 'PT1H');
    }

    this.form.setValue({
      start: data.start ? utils.toISOString(data.start) : '',
      stop: data.stop ? utils.toISOString(data.stop) : '',
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
      const url = this.yamcs.yamcsClient.getParameterValuesDownloadURL(this.yamcs.instance!, dlOptions);
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
