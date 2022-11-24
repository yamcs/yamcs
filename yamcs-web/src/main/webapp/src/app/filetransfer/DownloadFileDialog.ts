import { Component, Inject, ViewChild } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatTableDataSource } from '@angular/material/table';
import { BehaviorSubject } from 'rxjs';
import { Bucket, FileTransferService, StorageClient } from '../client';
import { YamcsService } from '../core/services/YamcsService';
import { ObjectSelector } from '../shared/forms/ObjectSelector';

@Component({
  selector: 'app-download-file-dialog',
  templateUrl: './DownloadFileDialog.html',
  styleUrls: ['./UploadFileDialog.css'],
})
export class DownloadFileDialog {

  localForm: UntypedFormGroup;
  remoteForm: UntypedFormGroup;

  service: FileTransferService;
  private storageClient: StorageClient;
  dataSource = new MatTableDataSource<Bucket>();

  displayedColumns = ['name'];

  selectedBucket$ = new BehaviorSubject<Bucket | null>(null);
  breadcrumb$ = new BehaviorSubject<BreadcrumbItem[]>([]);

  @ViewChild('selector')
  objectSelector: ObjectSelector;

  constructor(
    private dialogRef: MatDialogRef<DownloadFileDialog>,
    readonly yamcs: YamcsService,
    formBuilder: UntypedFormBuilder,
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
      object: ['', []],
    });
    this.remoteForm = formBuilder.group({
      remotePath: ['', Validators.required],
      source: [firstSource, Validators.required],
      destination: [firstDestination, Validators.required],
      reliable: [true, []],
    });
  }

  selectBucket(bucket: Bucket) {
    this.selectedBucket$.next(bucket);
  }

  startTransfer() {
    this.yamcs.yamcsClient.createFileTransfer(this.yamcs.instance!, this.service.name, {
      direction: 'DOWNLOAD',
      bucket: this.selectedBucket$.value!.name,
      objectName: this.objectSelector.currentPrefix$.value || "",
      remotePath: this.remoteForm.value['remotePath'],
      source: this.remoteForm.value['source'],
      destination: this.remoteForm.value['destination'],
      uploadOptions: {
        reliable: this.remoteForm.value['reliable']
      }
    }).then(() => {
      this.dialogRef.close();
    });
  }

  updateBreadcrumb(prefix: string | null) {
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
