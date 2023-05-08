import { Component, Inject } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { MAT_LEGACY_DIALOG_DATA, MatLegacyDialogRef } from '@angular/material/legacy-dialog';
import { StorageClient } from '../../client';
import { ConfigService } from '../../core/services/ConfigService';
import { YamcsService } from '../../core/services/YamcsService';
import { FilenamePipe } from '../../shared/pipes/FilenamePipe';

@Component({
  selector: 'app-rename-display-dialog',
  templateUrl: './RenameDisplayDialog.html',
})
export class RenameDisplayDialog {

  filenameForm: UntypedFormGroup;

  private storageClient: StorageClient;
  private bucket: string;

  constructor(
    private dialogRef: MatLegacyDialogRef<RenameDisplayDialog>,
    formBuilder: UntypedFormBuilder,
    yamcs: YamcsService,
    configService: ConfigService,
    filenamePipe: FilenamePipe,
    @Inject(MAT_LEGACY_DIALOG_DATA) readonly data: any,
  ) {
    this.storageClient = yamcs.createStorageClient();
    this.bucket = configService.getDisplayBucket();

    const filename = filenamePipe.transform(this.data.name);
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
