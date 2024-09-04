import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { EventSeverity, MessageService, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';

const defaultSeverity: EventSeverity = 'INFO';
const defaultSource: string[] = [];

@Component({
  standalone: true,
  selector: 'app-create-event-query-dialog',
  templateUrl: './create-event-query-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class CreateEventQueryDialogComponent {

  form = new FormGroup({
    name: new FormControl<string>('', Validators.required),
    filter: new FormControl<string>(''),
    severity: new FormControl<string>(defaultSeverity, Validators.required),
    source: new FormControl<string[]>(defaultSource),
    shared: new FormControl<boolean>(false, Validators.required),
  });

  constructor(
    private dialogRef: MatDialogRef<CreateEventQueryDialogComponent>,
    private yamcs: YamcsService,
    private messageService: MessageService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    this.form.patchValue({
      filter: data.filter || undefined,
      severity: data.severity,
      source: data.source,
    });
  }

  save() {
    const { value: fv } = this.form;
    this.yamcs.yamcsClient.createQuery(this.yamcs.instance!, 'events', {
      name: fv.name!,
      shared: fv.shared ?? false,
      query: {
        filter: fv.filter ?? undefined,
        source: fv.source ?? defaultSource,
        severity: fv.severity ?? defaultSeverity,
      },
    }).then(query => this.dialogRef.close(query))
      .catch(err => this.messageService.showError(err));
  }
}
