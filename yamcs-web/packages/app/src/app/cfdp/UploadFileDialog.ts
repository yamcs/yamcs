import { Component } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MatTableDataSource } from '@angular/material';
import { Bucket, Instance, StorageClient } from '@yamcs/client';
import { BehaviorSubject } from 'rxjs';
import { YamcsService } from '../core/services/YamcsService';

@Component({
  selector: 'app-upload-file-dialog',
  templateUrl: './UploadFileDialog.html',
  styleUrls: ['./UploadFileDialog.css'],
})
export class UploadFileDialog {

  instance: Instance;
  localForm: FormGroup;
  remoteForm: FormGroup;

  private storageClient: StorageClient;
  dataSource = new MatTableDataSource<Bucket>();

  displayedColumns = ['name'];

  selectedBucket$ = new BehaviorSubject<Bucket | null>(null);

  constructor(
    private dialogRef: MatDialogRef<UploadFileDialog>,
    private yamcs: YamcsService,
    formBuilder: FormBuilder,
  ) {
    this.instance = yamcs.getInstance();
    this.storageClient = yamcs.createStorageClient();
    this.storageClient.getBuckets(this.instance.name).then(buckets => {
      this.dataSource.data = buckets || [];
    });
    this.localForm = formBuilder.group({
      object: ['', Validators.required],
    });
    this.remoteForm = formBuilder.group({
      destination: ['', Validators.required],
      reliable: [true, []],
    });
  }

  selectBucket(bucket: Bucket) {
    this.selectedBucket$.next(bucket);
  }

  startTransfer() {
    const bucket = this.selectedBucket$.value!.name;
    const objectName = this.localForm.value['object'];
    const remoteFile = this.remoteForm.value['destination'];
    const reliable = this.remoteForm.value['reliable'];
    this.yamcs.getInstanceClient()!.uploadCfdpFile(bucket, objectName, remoteFile, reliable).then(() => {
      this.dialogRef.close();
    });
  }
}
