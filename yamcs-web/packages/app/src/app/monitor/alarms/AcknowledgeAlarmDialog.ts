import { Component, Inject } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  selector: 'app-acknowledge-alarm-dialog',
  templateUrl: './AcknowledgeAlarmDialog.html',
})
export class AcknowledgeAlarmDialog {

  formGroup: FormGroup;

  constructor(
    private dialogRef: MatDialogRef<AcknowledgeAlarmDialog>,
    formBuilder: FormBuilder,
    private yamcs: YamcsService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    this.formGroup = formBuilder.group({
      'comment': undefined,
    });
  }

  async acknowledge() {
    console.log('do ack');
    this.dialogRef.close();
  }
}
