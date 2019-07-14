import { Component, Inject } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { StorageClient } from '@yamcs/client';
import { YamcsService } from '../../core/services/YamcsService';
import { FilenamePipe } from '../../shared/pipes/FilenamePipe';

@Component({
  selector: 'app-rename-layout-dialog',
  templateUrl: './RenameLayoutDialog.html',
})
export class RenameLayoutDialog {

  filenameForm: FormGroup;
  storageClient: StorageClient;

  constructor(
    private dialogRef: MatDialogRef<RenameLayoutDialog>,
    formBuilder: FormBuilder,
    yamcs: YamcsService,
    filenamePipe: FilenamePipe,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    const filename = filenamePipe.transform(this.data.name);
    this.filenameForm = formBuilder.group({
      name: [filename, [Validators.required]],
    });

    this.storageClient = yamcs.createStorageClient();
  }

  async rename() {
    let prefix;
    const idx = this.data.name.lastIndexOf('/');
    if (idx !== -1) {
      prefix = this.data.name.substring(0, idx + 1);
    }

    const response = await this.storageClient.getObject('_global', this.data.bucket, this.data.name);
    const blob = await response.blob();

    const newObjectName = (prefix || '') + this.filenameForm.get('name')!.value;
    await this.storageClient.uploadObject('_global', this.data.bucket, newObjectName, blob);
    await this.storageClient.deleteObject('_global', this.data.bucket, this.data.name);
    this.dialogRef.close(newObjectName);
  }
}
