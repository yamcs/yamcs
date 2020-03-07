import { Component, Inject } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { Alarm, EditAlarmOptions } from '../client';
import { YamcsService } from '../core/services/YamcsService';
import * as utils from '../shared/utils';

@Component({
  selector: 'app-shelve-alarm-dialog',
  templateUrl: './ShelveAlarmDialog.html',
})
export class ShelveAlarmDialog {

  formGroup: FormGroup;

  constructor(
    private dialogRef: MatDialogRef<ShelveAlarmDialog>,
    formBuilder: FormBuilder,
    private yamcs: YamcsService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    this.formGroup = formBuilder.group({
      'duration': 'PT2H',
      'comment': undefined,
    });
  }

  async shelve() {
    const alarms = this.data.alarms as Alarm[];
    const comment = this.formGroup.get('comment')!.value;
    let duration = null;
    if (this.formGroup.get('duration')!.value !== 'UNLIMITED') {
      const durationString = this.formGroup.get('duration')!.value;
      duration = utils.convertDurationToMillis(durationString);
    }

    for (const alarm of alarms) {
      const processor = this.yamcs.getProcessor();
      const options: EditAlarmOptions = {
        state: 'shelved',
      };
      if (comment) {
        options.comment = comment;
      }
      if (duration) {
        options.shelveDuration = duration;
      }
      const alarmId = alarm.id.namespace + '/' + alarm.id.name;
      this.yamcs.yamcsClient.editAlarm(processor.instance, processor.name, alarmId, alarm.seqNum, options);
    }
    this.dialogRef.close();
  }
}
