import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
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
import { ItemStylesComponent } from '../bands/item-band/item-styles/item-styles.component';

@Component({
  selector: 'app-edit-activity-dialog',
  templateUrl: './edit-activity-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ItemStylesComponent, WebappSdkModule],
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
      start: [item.start, Validators.required],
      autoStart: [item.autoStart, Validators.required],
      duration: [item.duration, Validators.required],
      tags: [item.tags || [], []],
    });
  }

  save() {
    const formValue = this.form.value;
    const options: SaveTimelineItemRequest = {
      type: 'ACTIVITY',
      name: formValue.name,
      start: utils.toISOString(formValue.start),
      duration: formValue.duration,
      tags: formValue.tags,
      autoStart: formValue.autoStart,
    };
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
