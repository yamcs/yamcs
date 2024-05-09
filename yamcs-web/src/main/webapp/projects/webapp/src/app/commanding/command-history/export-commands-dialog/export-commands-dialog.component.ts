import { Component, Inject, OnDestroy } from '@angular/core';
import { UntypedFormControl, UntypedFormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { DownloadCommandsOptions, WebappSdkModule, YaSelectOption, YamcsService, utils } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';

@Component({
  standalone: true,
  templateUrl: './export-commands-dialog.component.html',
  imports: [
    WebappSdkModule,
  ],
})
export class ExportCommandsDialogComponent implements OnDestroy {

  delimiterOptions: YaSelectOption[] = [
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
  });

  constructor(
    private dialogRef: MatDialogRef<ExportCommandsDialogComponent>,
    private yamcs: YamcsService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
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
      const dlOptions: DownloadCommandsOptions = {
        delimiter: this.form.value['delimiter'],
      };
      if (this.form.value['start']) {
        dlOptions.start = utils.toISOString(this.form.value['start']);
      }
      if (this.form.value['stop']) {
        dlOptions.stop = utils.toISOString(this.form.value['stop']);
      }
      const url = this.yamcs.yamcsClient.getCommandsDownloadURL(this.yamcs.instance!, dlOptions);
      this.downloadURL$.next(url);
    } else {
      this.downloadURL$.next(null);
    }
  }

  ngOnDestroy() {
    this.formChangeSubscription?.unsubscribe();
  }
}
