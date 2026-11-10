import { Component, Inject } from '@angular/core';
import {
  UntypedFormBuilder,
  UntypedFormGroup,
  Validators,
} from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import {
  MessageService,
  SaveTimelineItemRequest,
  TimelineItem,
  WebappSdkModule,
  YaSelectOption,
  YamcsService,
  utils,
} from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-edit-activity-dialog',
  templateUrl: './edit-activity-dialog.component.html',
  imports: [WebappSdkModule],
})
export class EditActivityDialogComponent {
  resolutionOptions: YaSelectOption[] = [
    { id: 'seconds', label: 'seconds' },
    { id: 'minutes', label: 'minutes' },
    { id: 'hours', label: 'hours' },
  ];
  startConstraintOptions: YaSelectOption[] = [
    { id: 'START_ON', label: 'Start on' },
  ];

  form: UntypedFormGroup;

  constructor(
    private dialogRef: MatDialogRef<EditActivityDialogComponent>,
    formBuilder: UntypedFormBuilder,
    private yamcs: YamcsService,
    private messageService: MessageService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    const item = data.item as TimelineItem;

    this.form = formBuilder.group({
      name: [item.name, Validators.required],
      start: [
        { value: item.start, disabled: this.hasPredecessors },
        Validators.required,
      ],
      autoStart: [item.autoStart, Validators.required],
      duration: [item.duration, Validators.required],
      tags: [item.tags || [], []],
    });
  }

  /**
   * The start of an activity with predecessors is derived from them
   */
  get hasPredecessors() {
    const item = this.data.item as TimelineItem;
    return !!item.predecessors?.length;
  }

  save() {
    const item = this.data.item as TimelineItem;
    const formValue = this.form.value;

    // The update replaces the item: send back what this dialog does not edit
    const options: SaveTimelineItemRequest = {
      type: 'ACTIVITY',
      name: formValue.name,
      duration: formValue.duration,
      tags: formValue.tags,
      autoStart: formValue.autoStart,
      properties: item.properties,
      activityDefinition: item.activityDefinition,
    };
    if (this.hasPredecessors) {
      options.predecessors = item.predecessors!.map((p) => ({
        itemId: p.itemId,
        startCondition: p.startCondition,
      }));
    } else {
      options.start = utils.toISOString(formValue.start);
    }
    this.yamcs.yamcsClient
      .updateTimelineItem(this.yamcs.instance!, this.data.item.id, options)
      .then((item) => this.dialogRef.close(item))
      .catch((err) => this.messageService.showError(err));
  }

  delete() {
    if (confirm(`Are you sure you want to delete this item?`)) {
      this.yamcs.yamcsClient
        .deleteTimelineItem(this.yamcs.instance!, this.data.item.id)
        .then(() => this.dialogRef.close())
        .catch((err) => this.messageService.showError(err));
    }
  }
}
