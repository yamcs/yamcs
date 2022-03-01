import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { StorageClient } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './CreateFolderDialog.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateFolderDialog {

  form: FormGroup;

  private storageClient: StorageClient;

  constructor(
    private dialogRef: MatDialogRef<CreateFolderDialog>,
    formBuilder: FormBuilder,
    yamcs: YamcsService,
    @Inject(MAT_DIALOG_DATA) readonly data: any
  ) {
    this.storageClient = yamcs.createStorageClient();
    this.form = formBuilder.group({
      name: ['', Validators.required],
    });
  }

  save() {
    let { path, bucketInstance, bucket } = this.data;
    // Full path should not have a leading slash
    if (path.startsWith('/')) {
      path = path.substring(1);
    }
    const folderName = this.form.value['name'];
    const objectName = path ? `${path}/${folderName}/` : `${folderName}/`;
    this.storageClient.uploadObject(bucketInstance, bucket, objectName, new Blob())
      .then(() => this.dialogRef.close(true));
  }
}
