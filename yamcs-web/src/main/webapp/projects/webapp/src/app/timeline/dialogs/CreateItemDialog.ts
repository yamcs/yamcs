import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MessageService, SelectOption, YamcsService, utils } from '@yamcs/webapp-sdk';

@Component({
  templateUrl: './CreateItemDialog.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateItemDialog {

  startConstraintOptions: SelectOption[] = [
    { id: 'START_ON', label: 'Start on' },
  ];

  form: UntypedFormGroup;

  constructor(
    private dialogRef: MatDialogRef<CreateItemDialog>,
    formBuilder: UntypedFormBuilder,
    private yamcs: YamcsService,
    private messageService: MessageService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    this.form = formBuilder.group({
      name: ['', Validators.required],
      start: [utils.toISOString(yamcs.getMissionTime()), Validators.required],
      duration: ['', Validators.required],
      tags: [[], []],
    });
  }

  save() {
    this.yamcs.yamcsClient.createTimelineItem(this.yamcs.instance!, {
      name: this.form.value['name'],
      start: utils.toISOString(this.form.value['start']),
      duration: this.form.value['duration'],
      tags: this.form.value['tags'],
      type: 'EVENT',
    }).then(item => this.dialogRef.close(item))
      .catch(err => this.messageService.showError(err));
  }
}
