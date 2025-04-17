import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import {
  MessageService,
  WebappSdkModule,
  YamcsService,
} from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-create-packet-query-dialog',
  templateUrl: './create-packet-query-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [WebappSdkModule],
})
export class CreatePacketQueryDialogComponent {
  form = new FormGroup({
    queryName: new FormControl<string>('', Validators.required),
    name: new FormControl<string>(''),
    link: new FormControl<string>(''),
    filter: new FormControl<string>(''),
    shared: new FormControl<boolean>(false, Validators.required),
  });

  constructor(
    private dialogRef: MatDialogRef<CreatePacketQueryDialogComponent>,
    private yamcs: YamcsService,
    private messageService: MessageService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    this.form.patchValue({
      name: data.name,
      link: data.link,
      filter: data.filter || undefined,
    });
  }

  save() {
    const { value: fv } = this.form;
    this.yamcs.yamcsClient
      .createQuery(this.yamcs.instance!, 'packets', {
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
