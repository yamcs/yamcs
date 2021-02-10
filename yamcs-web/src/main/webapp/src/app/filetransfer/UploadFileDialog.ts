import { Component, Inject, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatTableDataSource } from '@angular/material/table';
import { BehaviorSubject } from 'rxjs';
import { Bucket, FileTransferService, StorageClient } from '../client';
import { MessageService } from '../core/services/MessageService';
import { YamcsService } from '../core/services/YamcsService';
import { ObjectSelector } from '../shared/forms/ObjectSelector';

@Component({
  selector: 'app-upload-file-dialog',
  templateUrl: './UploadFileDialog.html',
  styleUrls: ['./UploadFileDialog.css'],
})
export class UploadFileDialog {

  localForm: FormGroup;
  remoteForm: FormGroup;

  service: FileTransferService;
  private storageClient: StorageClient;
  dataSource = new MatTableDataSource<Bucket>();

  displayedColumns = ['name'];

  selectedBucket$ = new BehaviorSubject<Bucket | null>(null);
  breadcrumb$ = new BehaviorSubject<BreadcrumbItem[]>([]);

  @ViewChild('selector')
  objectSelector: ObjectSelector;

  constructor(
    private dialogRef: MatDialogRef<UploadFileDialog>,
    readonly yamcs: YamcsService,
    formBuilder: FormBuilder,
    private messageService: MessageService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    this.service = data.service;
    const firstSource = this.service.localEntities.length ? this.service.localEntities[0].name : '';
    const firstDestination = this.service.remoteEntities.length ? this.service.remoteEntities[0].name : '';

    this.storageClient = yamcs.createStorageClient();
    this.storageClient.getBuckets('_global').then(buckets => {
      this.dataSource.data = buckets || [];
    });
    this.localForm = formBuilder.group({
      object: ['', Validators.required],
    });
    this.remoteForm = formBuilder.group({
      remotePath: ['', this.service.capabilities.remotePath ? Validators.required : []],
      source: [firstSource, Validators.required],
      destination: [firstDestination, Validators.required],
      reliable: [true, []],
    });
  }

  selectBucket(bucket: Bucket) {
    this.selectedBucket$.next(bucket);
  }

  async startTransfer() {
    const objectNames: string[] = this.localForm.value['object'].split('|');
    const promises = objectNames.map((name) =>
      this.yamcs.yamcsClient.createFileTransfer(this.yamcs.instance!, this.service.name, {
        direction: 'UPLOAD',
        bucket: this.selectedBucket$.value!.name,
        objectName: name,
        remotePath: this.remoteForm.value['remotePath'],
        source: this.remoteForm.value['source'],
        destination: this.remoteForm.value['destination'],
        uploadOptions: {
          reliable: this.remoteForm.value['reliable']
        }
      })
    );

    // Collect combined success/failure result
    let anyError;
    let errorCount = 0;
    for (const promise of promises) {
      try {
        await promise;
      } catch (err) {
        anyError = err;
        errorCount++;
      }
    }

    if (anyError) {
      if (errorCount === 1) {
        this.messageService.showError(anyError);
      } else if (errorCount === promises.length) {
        this.messageService.showError('Failed to start any of the selected transfers. See server log.');
      } else {
        this.messageService.showError('Some of the transfers failed to start. See server log.');
      }
    }

    this.dialogRef.close();
  }

  updateBreadcrumb(prefix: string) {
    if (!prefix) {
      this.breadcrumb$.next([]);
      return;
    }

    if (prefix.endsWith('/')) {
      prefix = prefix.substr(0, prefix.length - 1);
    }

    const items: BreadcrumbItem[] = [];
    const parts = prefix.split('/');
    for (let i = 0; i < parts.length; i++) {
      items.push({
        name: parts[i],
        prefix: parts.slice(0, i + 1).join('/'),
      });
    }
    this.breadcrumb$.next(items);
  }

  changePrefix(prefix: string) {
    if (prefix) {
      prefix = prefix + '/';
    }
    this.objectSelector.changePrefix(prefix);
  }
}

interface BreadcrumbItem {
  name: string;
  prefix: string;
}
