import { ChangeDetectionStrategy, Component } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { MatDialogRef } from '@angular/material/dialog';
import { ActivityDefinition, MessageService, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  templateUrl: './start-manual-activity-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class StartManualActivityDialogComponent {

  form: UntypedFormGroup;

  constructor(
    private dialogRef: MatDialogRef<StartManualActivityDialogComponent>,
    formBuilder: UntypedFormBuilder,
    readonly yamcs: YamcsService,
    private messageService: MessageService,
  ) {
    this.form = formBuilder.group({
      name: ['', [Validators.required]],
    });
  }

  onConfirm() {
    const options = this.createActivityDefinition();
    this.yamcs.yamcsClient.startActivity(this.yamcs.instance!, options)
      .then(activity => this.dialogRef.close())
      .catch(err => this.messageService.showError(err));
  }

  private createActivityDefinition(): ActivityDefinition {
    const formValue = this.form.value;
    const options: ActivityDefinition = {
      type: 'MANUAL',
      args: {
        name: formValue['name'],
      },
    };
    return options;
  }
}
