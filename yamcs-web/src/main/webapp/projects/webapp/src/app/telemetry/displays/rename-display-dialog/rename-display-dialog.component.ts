import { Component, Inject } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { ConfigService, StorageClient, WebappSdkModule, YamcsService, utils } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-rename-display-dialog',
  templateUrl: './rename-display-dialog.component.html',
  imports: [
    WebappSdkModule,
  ],
})
export class RenameDisplayDialogComponent {

  filenameForm: UntypedFormGroup;

  private storageClient: StorageClient;
  private bucket: string;

  constructor(
    private dialogRef: MatDialogRef<RenameDisplayDialogComponent>,
    formBuilder: UntypedFormBuilder,
    yamcs: YamcsService,
    configService: ConfigService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    this.storageClient = yamcs.createStorageClient();
    this.bucket = configService.getDisplayBucket();

    const filename = utils.getFilename(this.data.name);
    this.filenameForm = formBuilder.group({
      name: [filename, [Validators.required]],
    });
  }

  async rename() {
    let prefix;
    const idx = this.data.name.lastIndexOf('/');
    if (idx !== -1) {
      prefix = this.data.name.substring(0, idx + 1);
    }

    const response = await this.storageClient.getObject(this.bucket, this.data.name);
    const blob = await response.blob();

    const newObjectName = (prefix || '') + this.filenameForm.get('name')!.value;
    await this.storageClient.uploadObject(this.bucket, newObjectName, blob);
    await this.storageClient.deleteObject(this.bucket, this.data.name);
    this.dialogRef.close(newObjectName);
  }
}
