import { Component, ElementRef, Inject, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material';
import { StorageClient } from '@yamcs/client';
import { BehaviorSubject } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  selector: 'app-upload-objects-dialog',
  templateUrl: './UploadObjectsDialog.html',
})
export class UploadObjectsDialog {

  formGroup: FormGroup;

  @ViewChild('files')
  filesInput: ElementRef;

  uploading$ = new BehaviorSubject<boolean>(false);

  private storageClient: StorageClient;

  constructor(
    private dialogRef: MatDialogRef<UploadObjectsDialog>,
    formBuilder: FormBuilder,
    yamcs: YamcsService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    this.storageClient = yamcs.createStorageClient();
    this.formGroup = formBuilder.group({
      path: [data.path, Validators.required],
      files: [null, Validators.required],
    });
  }

  save() {
    let path: string = this.formGroup.get('path')!.value.trim();

    // Full path should not have a leading slash
    if (path.startsWith('/')) {
      path = path.substring(1);
    }
    if (path.endsWith('/')) {
      path = path.substring(0, path.length - 1);
    }

    const files: {[key: string]: File} = this.filesInput.nativeElement.files;

    const uploadPromises = [];
    this.uploading$.next(true);
    for (const key in files) {
      if (!isNaN(parseInt(key, 10))) {
        const file = files[key];
        const fullPath = path ? path + '/' + file.name : file.name;

        const bucketInstance = this.data.bucketInstance;
        const bucket = this.data.bucket;
        const promise = this.storageClient.uploadObject(bucketInstance, bucket, fullPath, file);
        uploadPromises.push(promise);
      }
    }

    Promise.all(uploadPromises).then(() => {
      this.uploading$.next(false);
      this.dialogRef.close(true);
    }).catch(() => {
      this.uploading$.next(false);
      this.dialogRef.close(true);
    });
  }
}
