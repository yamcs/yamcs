import { Component, Inject } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { Alarm, EditAlarmOptions } from '../client';
import { YamcsService } from '../core/services/YamcsService';

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
    const alarms = this.data.alarms as Alarm[];
    const comment = this.formGroup.get('comment')!.value;

    for (const alarm of alarms) {
      const processor = this.yamcs.getProcessor();
      const options: EditAlarmOptions = {
        state: 'acknowledged',
      };
      if (comment) {
        options.comment = comment;
      }
      const alarmId = alarm.id.namespace + '/' + alarm.id.name;
      this.yamcs.yamcsClient.editAlarm(processor.instance, processor.name, alarmId, alarm.seqNum, options);
    }
    this.dialogRef.close();
  }
}
