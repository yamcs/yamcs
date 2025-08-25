import { Component, Inject } from '@angular/core';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogRef,
  MatDialogTitle,
} from '@angular/material/dialog';
import { BaseComponent } from '../../abc/BaseComponent';
import * as utils from '../../utils';
import { dateRangeValidator } from '../../validators';
import { YaButton } from '../button/button.component';
import { YaDateTimeInput } from '../date-time-input/date-time-input.component';
import { YaField } from '../field/field.component';
import { YaWarningMessage } from '../warning-message/warning-message.component';

@Component({
  selector: 'ya-start-replay-dialog',
  templateUrl: './start-replay-dialog.component.html',
  imports: [
    MatDialogActions,
    MatDialogClose,
    MatDialogContent,
    MatDialogTitle,
    ReactiveFormsModule,
    YaDateTimeInput,
    YaButton,
    YaField,
    YaWarningMessage,
  ],
})
export class StartReplayDialogComponent extends BaseComponent {
  form = new FormGroup(
    {
      name: new FormControl<string | null>(null, Validators.required),
      start: new FormControl<string | null>(null, Validators.required),
      stop: new FormControl<string | null>(null),
    },
    {
      validators: [dateRangeValidator('start', 'stop')],
    },
  );

  constructor(
    private dialogRef: MatDialogRef<StartReplayDialogComponent>,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    super();
    let initialStart = this.yamcs.getMissionTime();
    let initialStop;

    if (this.data) {
      if (this.data.start) {
        initialStart = this.data.start;
      }
      if (this.data.stop) {
        initialStop = this.data.stop;
      }
    }

    this.form.setValue({
      name: utils.generateRandomName(),
      start: utils.toISOString(initialStart),
      stop: initialStop ? utils.toISOString(initialStop) : null,
    });
  }

  start() {
    const replayConfig: { [key: string]: any } = {
      start: utils.toISOString(this.form.value.start!),
      endAction: 'STOP',
    };
    if (this.form.value.stop) {
      replayConfig.stop = utils.toISOString(this.form.value.stop);
    }

    this.dialogRef.close({
      instance: this.yamcs.instance!,
      name: this.form.value.name,
      type: 'Archive', // TODO make configurable?
      persistent: true,
      config: JSON.stringify(replayConfig),
    });
  }
}
