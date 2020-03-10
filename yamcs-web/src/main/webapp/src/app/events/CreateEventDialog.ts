import { Component, Inject } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { YamcsService } from '../core/services/YamcsService';
import * as utils from '../shared/utils';

@Component({
  selector: 'app-create-event-dialog',
  templateUrl: './CreateEventDialog.html',
})
export class CreateEventDialog {

  form: FormGroup;

  constructor(
    private dialogRef: MatDialogRef<CreateEventDialog>,
    formBuilder: FormBuilder,
    private yamcs: YamcsService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    this.form = formBuilder.group({
      message: ['', Validators.required],
      severity: 'INFO',
      time: [utils.printLocalDate(yamcs.getMissionTime(), 'hhmmss'), Validators.required],
    });
  }

  save() {
    this.yamcs.yamcsClient.createEvent(this.yamcs.getInstance(), {
      message: this.form.value['message'],
      severity: this.form.value['severity'],
      time: utils.toISOString(this.form.value['time']),
    }).then(event => this.dialogRef.close(event));
  }
}
