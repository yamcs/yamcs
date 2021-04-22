import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MessageService } from '../../core/services/MessageService';
import { YamcsService } from '../../core/services/YamcsService';
import { Option } from '../../shared/forms/Select';
import * as utils from '../../shared/utils';

@Component({
  templateUrl: './CreateItemDialog.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateItemDialog {

  startConstraintOptions: Option[] = [
    { id: 'START_ON', label: 'Start on' },
  ];

  form: FormGroup;

  constructor(
    private dialogRef: MatDialogRef<CreateItemDialog>,
    formBuilder: FormBuilder,
    private yamcs: YamcsService,
    private messageService: MessageService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    this.form = formBuilder.group({
      name: ['', Validators.required],
      start: [utils.toISOString(yamcs.getMissionTime()), Validators.required],
      duration: ['', Validators.required],
    });
  }

  save() {
    this.yamcs.yamcsClient.createTimelineItem(this.yamcs.instance!, {
      name: this.form.value['name'],
      start: utils.toISOString(this.form.value['start']),
      duration: this.form.value['duration'],
      type: 'EVENT',
    }).then(item => this.dialogRef.close(item))
      .catch(err => this.messageService.showError(err));
  }
}
