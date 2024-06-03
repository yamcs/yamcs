import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { AcknowledgeAlarmOptions, Alarm, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-acknowledge-alarm-dialog',
  templateUrl: './acknowledge-alarm-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class AcknowledgeAlarmDialogComponent {

  formGroup: UntypedFormGroup;

  constructor(
    private dialogRef: MatDialogRef<AcknowledgeAlarmDialogComponent>,
    formBuilder: UntypedFormBuilder,
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
      const options: AcknowledgeAlarmOptions = {};
      if (comment) {
        options.comment = comment;
      }
      const alarmName = alarm.id.namespace + (alarm.id.name ? '/' + alarm.id.name : '');
      this.yamcs.yamcsClient.acknowledgeAlarm(this.yamcs.instance!, this.yamcs.processor!, alarmName, alarm.seqNum, options);
    }
    this.dialogRef.close();
  }
}
