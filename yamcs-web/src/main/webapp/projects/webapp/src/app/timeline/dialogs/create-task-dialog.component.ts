import {
  ChangeDetectionStrategy,
  Component,
  Inject,
  OnDestroy,
} from '@angular/core';
import {
  UntypedFormBuilder,
  UntypedFormGroup,
  Validators,
} from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import {
  MessageService,
  SaveTimelineItemRequest,
  WebappSdkModule,
  YaSelectOption,
  YamcsService,
  utils,
} from '@yamcs/webapp-sdk';
import { addMinutes, roundToNearestMinutes } from 'date-fns';
import { Subscription } from 'rxjs';

@Component({
  templateUrl: './create-task-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [WebappSdkModule],
})
export class CreateTaskDialogComponent implements OnDestroy {
  startConstraintOptions: YaSelectOption[] = [
    { id: 'START_ON', label: 'Start on' },
  ];

  form: UntypedFormGroup;
  private formSubscription: Subscription;

  constructor(
    private dialogRef: MatDialogRef<CreateTaskDialogComponent>,
    formBuilder: UntypedFormBuilder,
    private yamcs: YamcsService,
    private messageService: MessageService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    this.form = formBuilder.group({
      name: ['', Validators.required],
      start: [
        utils.toISOString(this.calculateInitialStart()),
        Validators.required,
      ],
      autoStart: [false, Validators.required],
      duration: ['0s'],
      tags: [[], []],
    });
  }

  /**
   * Returns the next half hour
   */
  private calculateInitialStart() {
    const now = this.yamcs.getMissionTime();
    return roundToNearestMinutes(addMinutes(now, 30), {
      nearestTo: 30,
      roundingMethod: 'floor',
    });
  }

  save() {
    const options: SaveTimelineItemRequest = {
      type: 'ACTIVITY',
      name: this.form.value['name'],
      start: utils.toISOString(this.form.value['start']),
      duration: this.form.value['duration'],
      tags: this.form.value['tags'],
      autoStart: this.form.value['autoStart'],
    };

    this.yamcs.yamcsClient
      .createTimelineItem(this.yamcs.instance!, options)
      .then((item) => this.dialogRef.close(item))
      .catch((err) => this.messageService.showError(err));
  }

  ngOnDestroy() {
    this.formSubscription?.unsubscribe();
  }
}
