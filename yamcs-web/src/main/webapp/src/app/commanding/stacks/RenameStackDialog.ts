import { Component, Inject } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { StorageClient } from '../../client';
import { ConfigService } from '../../core/services/ConfigService';
import { YamcsService } from '../../core/services/YamcsService';
import { BasenamePipe } from '../../shared/pipes/BasenamePipe';

@Component({
  selector: 'app-rename-stack-dialog',
  templateUrl: './RenameStackDialog.html',
})
export class RenameStackDialog {

  filenameForm: UntypedFormGroup;

  private storageClient: StorageClient;
  private bucket: string;

  constructor(
    private dialogRef: MatDialogRef<RenameStackDialog>,
    formBuilder: UntypedFormBuilder,
    yamcs: YamcsService,
    basenamePipe: BasenamePipe,
    configService: ConfigService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    this.storageClient = yamcs.createStorageClient();
    this.bucket = configService.getConfig().stackBucket;

    const basename = basenamePipe.transform(this.data.name);
    this.filenameForm = formBuilder.group({
      name: [basename, [Validators.required]],
    });
  }

  async rename() {
    let prefix;
    const idx = this.data.name.lastIndexOf('/');
    if (idx !== -1) {
      prefix = this.data.name.substring(0, idx + 1);
    }

    const response = await this.storageClient.getObject('_global', this.bucket, this.data.name);
    const blob = await response.blob();

    const newObjectName = (prefix || '') + this.filenameForm.get('name')!.value + '.xml';
    await this.storageClient.uploadObject('_global', this.bucket, newObjectName, blob);
    await this.storageClient.deleteObject('_global', this.bucket, this.data.name);
    this.dialogRef.close(newObjectName);
  }
}
