import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { EventSeverity, MessageService, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';

const defaultSeverity: EventSeverity = 'INFO';
const defaultSource: string[] = [];

@Component({
  standalone: true,
  selector: 'app-edit-event-query-dialog',
  templateUrl: './edit-event-query-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class EditEventQueryDialogComponent {

  form = new FormGroup({
    name: new FormControl<string>('', Validators.required),
    filter: new FormControl<string>(''),
    severity: new FormControl<string>(defaultSeverity, Validators.required),
    source: new FormControl<string[]>(defaultSource),
    shared: new FormControl<boolean>(false, Validators.required),
  });

  constructor(
    private dialogRef: MatDialogRef<EditEventQueryDialogComponent>,
    private yamcs: YamcsService,
    private messageService: MessageService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    const { query } = data;
    this.form.patchValue({
      name: query.name,
      filter: query.query.filter || undefined,
      severity: query.query.severity,
      source: query.query.source,
      shared: query.shared,
    });
  }

  save() {
    const { value: fv } = this.form;
    const queryId: string = this.data.query.id;
    this.yamcs.yamcsClient.editQuery(this.yamcs.instance!, 'events', queryId, {
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
