import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MessageService } from '../../core/services/MessageService';
import { YamcsService } from '../../core/services/YamcsService';
import { Option } from '../../shared/forms/Select';
import * as utils from '../../shared/utils';

@Component({
  templateUrl: './EditItemDialog.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditItemDialog {

  resolutionOptions: Option[] = [
    { id: 'seconds', label: 'seconds' },
    { id: 'minutes', label: 'minutes' },
    { id: 'hours', label: 'hours' }
  ];
  startConstraintOptions: Option[] = [
    { id: 'START_ON', label: 'Start on' },
  ];

  form: FormGroup;

  constructor(
    private dialogRef: MatDialogRef<EditItemDialog>,
    formBuilder: FormBuilder,
    private yamcs: YamcsService,
    private messageService: MessageService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    const item = data.item;
    this.form = formBuilder.group({
      name: [item.name, Validators.required],
      start: [item.start, Validators.required],
      duration: [item.duration, Validators.required],
    });
  }

  save() {
    this.yamcs.yamcsClient.updateTimelineItem(this.yamcs.instance!, this.data.item.id, {
      ...this.data.item,
      name: this.form.value['name'],
      start: utils.toISOString(this.form.value['start']),
      duration: this.form.value['duration'],
    }).then(item => this.dialogRef.close(item))
      .catch(err => this.messageService.showError(err));
  }

  delete() {
    if (confirm(`Are you sure you want to delete this item?`)) {
      this.yamcs.yamcsClient.deleteTimelineItem(this.yamcs.instance!, this.data.item.id)
        .then(() => this.dialogRef.close())
        .catch(err => this.messageService.showError(err));
    }
  }
}
