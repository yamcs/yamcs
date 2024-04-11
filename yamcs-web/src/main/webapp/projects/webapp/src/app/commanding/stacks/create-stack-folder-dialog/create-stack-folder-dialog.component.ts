import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { StorageClient, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-create-stack-folder-dialog',
  templateUrl: './create-stack-folder-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class CreateStackFolderDialogComponent {

  form: UntypedFormGroup;

  private storageClient: StorageClient;

  constructor(
    private dialogRef: MatDialogRef<CreateStackFolderDialogComponent>,
    formBuilder: UntypedFormBuilder,
    yamcs: YamcsService,
    @Inject(MAT_DIALOG_DATA) readonly data: any
  ) {
    this.storageClient = yamcs.createStorageClient();
    this.form = formBuilder.group({
      name: ['', Validators.required],
    });
  }

  save() {
    let { path, bucket } = this.data;
    // Full path should not have a leading slash
    if (path.startsWith('/')) {
      path = path.substring(1);
    }
    const folderName = this.form.value['name'];
    const objectName = path ? `${path}/${folderName}/` : `${folderName}/`;
    this.storageClient.uploadObject(bucket, objectName, new Blob())
      .then(() => this.dialogRef.close(true));
  }
}
