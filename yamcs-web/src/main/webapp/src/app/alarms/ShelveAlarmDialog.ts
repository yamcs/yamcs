import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup } from '@angular/forms';
import { MatLegacyDialogRef, MAT_LEGACY_DIALOG_DATA } from '@angular/material/legacy-dialog';
import { Alarm, ShelveAlarmOptions } from '../client';
import { YamcsService } from '../core/services/YamcsService';
import { Option } from '../shared/forms/Select';
import * as utils from '../shared/utils';

@Component({
  selector: 'app-shelve-alarm-dialog',
  templateUrl: './ShelveAlarmDialog.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ShelveAlarmDialog {

  formGroup: UntypedFormGroup;

  durationOptions: Option[] = [
    { id: 'PT15M', label: '15 minutes' },
    { id: 'PT30M', label: '30 minutes' },
    { id: 'PT1H', label: '1 hour' },
    { id: 'PT2H', label: '2 hours' },
    { id: 'P1D', label: '1 day' },
    { id: 'UNLIMITED', label: 'unlimited' },
  ];

  constructor(
    private dialogRef: MatLegacyDialogRef<ShelveAlarmDialog>,
    formBuilder: UntypedFormBuilder,
    private yamcs: YamcsService,
    @Inject(MAT_LEGACY_DIALOG_DATA) readonly data: any,
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
      const options: ShelveAlarmOptions = {};
      if (comment) {
        options.comment = comment;
      }
      if (duration) {
        options.shelveDuration = duration;
      }
      const alarmName = alarm.id.namespace + (alarm.id.name ? '/' + alarm.id.name : '');
      this.yamcs.yamcsClient.shelveAlarm(this.yamcs.instance!, this.yamcs.processor!, alarmName, alarm.seqNum, options);
    }
    this.dialogRef.close();
  }
}
