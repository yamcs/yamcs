import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MessageService, SelectOption, UpdateTimelineItemRequest, YamcsService, utils } from '@yamcs/webapp-sdk';

@Component({
  templateUrl: './EditItemDialog.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditItemDialog {

  resolutionOptions: SelectOption[] = [
    { id: 'seconds', label: 'seconds' },
    { id: 'minutes', label: 'minutes' },
    { id: 'hours', label: 'hours' }
  ];
  startConstraintOptions: SelectOption[] = [
    { id: 'START_ON', label: 'Start on' },
  ];

  form: UntypedFormGroup;

  constructor(
    private dialogRef: MatDialogRef<EditItemDialog>,
    formBuilder: UntypedFormBuilder,
    private yamcs: YamcsService,
    private messageService: MessageService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    const item = data.item;
    this.form = formBuilder.group({
      name: [item.name, Validators.required],
      start: [item.start, Validators.required],
      duration: [item.duration, Validators.required],
      tags: [item.tags || [], []],
    });
  }

  save() {
    const formValue = this.form.value;
    const options: UpdateTimelineItemRequest = {
      name: formValue.name,
      start: utils.toISOString(formValue.start),
      duration: formValue.duration,
      tags: formValue.tags,
    };
    this.yamcs.yamcsClient.updateTimelineItem(this.yamcs.instance!, this.data.item.id, options).then(item => this.dialogRef.close(item))
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
