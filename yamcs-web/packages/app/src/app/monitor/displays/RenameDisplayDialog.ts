import { Component, Inject } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material';
import { Instance, StorageClient } from '@yamcs/client';
import { YamcsService } from '../../core/services/YamcsService';
import { FilenamePipe } from '../../shared/pipes/FilenamePipe';

@Component({
  selector: 'app-rename-display-dialog',
  templateUrl: './RenameDisplayDialog.html',
})
export class RenameDisplayDialog {

  filenameForm: FormGroup;

  private instance: Instance;
  private storageClient: StorageClient;

  constructor(
    private dialogRef: MatDialogRef<RenameDisplayDialog>,
    formBuilder: FormBuilder,
    private yamcs: YamcsService,
    filenamePipe: FilenamePipe,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    this.instance = yamcs.getInstance();
    this.storageClient = yamcs.createStorageClient();

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

    const response = await this.storageClient.getObject(this.instance.name, 'displays', this.data.name);
    const blob = await response.blob();

    const newObjectName = (prefix || '') + this.filenameForm.get('name')!.value;
    await this.storageClient.uploadObject(this.instance.name, 'displays', newObjectName, blob);
    await this.storageClient.deleteObject(this.instance.name, 'displays', this.data.name);
    this.dialogRef.close(newObjectName);
  }
}
