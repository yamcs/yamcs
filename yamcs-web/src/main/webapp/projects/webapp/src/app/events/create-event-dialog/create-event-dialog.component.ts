import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { WebappSdkModule, YaSelectOption, YamcsService, utils } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-create-event-dialog',
  templateUrl: './create-event-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class CreateEventDialogComponent {

  form: UntypedFormGroup;

  severityOptions: YaSelectOption[] = [
    { id: 'INFO', label: 'INFO' },
    { id: 'WATCH', label: 'WATCH' },
    { id: 'WARNING', label: 'WARNING' },
    { id: 'DISTRESS', label: 'DISTRESS' },
    { id: 'CRITICAL', label: 'CRITICAL' },
    { id: 'SEVERE', label: 'SEVERE' },
  ];

  constructor(
    private dialogRef: MatDialogRef<CreateEventDialogComponent>,
    formBuilder: UntypedFormBuilder,
    private yamcs: YamcsService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    this.form = formBuilder.group({
      message: ['', Validators.required],
      severity: 'INFO',
      time: [utils.toISOString(yamcs.getMissionTime()), Validators.required],
    });
  }

  save() {
    this.yamcs.yamcsClient.createEvent(this.yamcs.instance!, {
      message: this.form.value['message'],
      severity: this.form.value['severity'],
      time: utils.toISOString(this.form.value['time']),
    }).then(event => this.dialogRef.close(event));
  }
}
