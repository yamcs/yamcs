import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import {
  MessageService,
  WebappSdkModule,
  YamcsService,
} from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-edit-packet-query-dialog',
  templateUrl: './edit-packet-query-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [WebappSdkModule],
})
export class EditPacketQueryDialogComponent {
  form = new FormGroup({
    queryName: new FormControl<string>('', Validators.required),
    name: new FormControl<string>(''),
    link: new FormControl<string>(''),
    filter: new FormControl<string>(''),
    shared: new FormControl<boolean>(false, Validators.required),
  });

  constructor(
    private dialogRef: MatDialogRef<EditPacketQueryDialogComponent>,
    private yamcs: YamcsService,
    private messageService: MessageService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    const { query } = data;
    this.form.patchValue({
      queryName: query.name,
      name: query.query.name || 'ANY',
      link: query.query.link || 'ANY',
      filter: query.query.filter || undefined,
      shared: query.shared,
    });
  }

  save() {
    const { value: fv } = this.form;
    const queryId: string = this.data.query.id;
    this.yamcs.yamcsClient
      .editQuery(this.yamcs.instance!, 'packets', queryId, {
        name: fv.queryName!,
        shared: fv.shared ?? false,
        query: {
          name: fv.name !== 'ANY' ? fv.name : undefined,
          link: fv.link !== 'ANY' ? fv.link : undefined,
          filter: fv.filter ?? undefined,
        },
      })
      .then((query) => this.dialogRef.close(query))
      .catch((err) => this.messageService.showError(err));
  }
}
