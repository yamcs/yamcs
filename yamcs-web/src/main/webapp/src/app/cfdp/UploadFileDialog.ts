import { Component } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef } from '@angular/material/dialog';
import { MatTableDataSource } from '@angular/material/table';
import { BehaviorSubject } from 'rxjs';
import { Bucket, Instance, StorageClient } from '../client';
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
    this.storageClient.getBuckets('_global').then(buckets => {
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
    this.yamcs.getInstanceClient()!.createCfdpTransfer({
      direction: 'UPLOAD',
      bucket: this.selectedBucket$.value!.name,
      objectName: this.localForm.value['object'],
      remotePath: this.remoteForm.value['destination'],
      uploadOptions: {
        reliable: this.remoteForm.value['reliable']
      }
    }).then(() => {
      this.dialogRef.close();
    });
  }
}
