import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material';
import { Component, Inject } from '@angular/core';
import { FormGroup, FormBuilder, Validators } from '@angular/forms';
import { YamcsService } from '../../core/services/YamcsService';

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
      time: [yamcs.getMissionTime().toISOString(), Validators.pattern(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/)],
    });
  }

  save() {
    this.yamcs.getInstanceClient()!.createEvent(this.form.value)
      .then(event => this.dialogRef.close(event));
  }
}
