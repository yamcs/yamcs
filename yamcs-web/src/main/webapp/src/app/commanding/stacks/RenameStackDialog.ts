import { Component, Inject } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { StorageClient } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';
import { BasenamePipe } from '../../shared/pipes/BasenamePipe';

@Component({
  selector: 'app-rename-stack-dialog',
  templateUrl: './RenameStackDialog.html',
})
export class RenameStackDialog {

  filenameForm: FormGroup;

  private storageClient: StorageClient;

  constructor(
    private dialogRef: MatDialogRef<RenameStackDialog>,
    formBuilder: FormBuilder,
    yamcs: YamcsService,
    basenamePipe: BasenamePipe,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    this.storageClient = yamcs.createStorageClient();

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

    const response = await this.storageClient.getObject('_global', 'stacks', this.data.name);
    const blob = await response.blob();

    const newObjectName = (prefix || '') + this.filenameForm.get('name')!.value + '.xml';
    await this.storageClient.uploadObject('_global', 'stacks', newObjectName, blob);
    await this.storageClient.deleteObject('_global', 'stacks', this.data.name);
    this.dialogRef.close(newObjectName);
  }
}
