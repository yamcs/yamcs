import { Component, Inject } from '@angular/core';
import {
  ReactiveFormsModule,
  UntypedFormBuilder,
  UntypedFormGroup,
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
import { YaButton } from '../button/button.component';
import { YaDateTimeInput } from '../date-time-input/date-time-input.component';
import { YaField } from '../field/field.component';

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
  ],
})
export class StartReplayDialogComponent extends BaseComponent {
  form: UntypedFormGroup;

  constructor(
    private dialogRef: MatDialogRef<StartReplayDialogComponent>,
    formBuilder: UntypedFormBuilder,
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

    this.form = formBuilder.group({
      name: [utils.generateRandomName(), Validators.required],
      start: [utils.toISOString(initialStart), [Validators.required]],
      stop: [initialStop ? utils.toISOString(initialStop) : ''],
    });
  }

  start() {
    const replayConfig: { [key: string]: any } = {
      start: utils.toISOString(this.form.value.start),
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
