import { Component, Inject, OnDestroy, ViewChild } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatTableDataSource } from '@angular/material/table';
import { BehaviorSubject } from 'rxjs';
import { Bucket, FileTransferService, RemoteFileListSubscription, StorageClient } from '../client';
import { MessageService } from '../core/services/MessageService';
import { YamcsService } from '../core/services/YamcsService';
import { ObjectSelector } from '../shared/forms/ObjectSelector';
import { RemoteFileSelector } from './RemoteFileSelector';

@Component({
  selector: 'app-download-file-dialog',
  templateUrl: './DownloadFileDialog.html',
  styleUrls: ['./DownloadFileDialog.css'],
})
export class DownloadFileDialog implements OnDestroy {
  isDownloadEnabled = false;
  isUploadEnabled = false;
  form: UntypedFormGroup;
  service: FileTransferService;
  private storageClient: StorageClient;
  dataSource = new MatTableDataSource<Bucket>();

  displayedColumns = ['name'];

  selectedBucket$ = new BehaviorSubject<Bucket | null>(null);
  breadcrumb$ = new BehaviorSubject<BreadcrumbItem[]>([]);
  remoteBreadcrumb$ = new BehaviorSubject<BreadcrumbItem[]>([]);
  lastFileListTime$ = new BehaviorSubject<string>('-');

  private fileListSubscription: RemoteFileListSubscription;

  @ViewChild('selector')
  objectSelector: ObjectSelector;

  @ViewChild('remoteSelector')
  remoteSelector: RemoteFileSelector;

  constructor(
    private dialogRef: MatDialogRef<DownloadFileDialog>,
    readonly yamcs: YamcsService,
    formBuilder: UntypedFormBuilder,
    private messageService: MessageService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    this.service = data.service;
    const firstSource = this.service.localEntities.length ? this.service.localEntities[0].name : '';
    const firstDestination = this.service.remoteEntities.length ? this.service.remoteEntities[0].name : '';

    this.storageClient = yamcs.createStorageClient();
    this.storageClient.getBuckets('_global').then(buckets => {
      this.dataSource.data = buckets || [];
      if (buckets) {
        this.selectBucket(buckets[0]);
      }
    });

    // Subscribe to remote file list updates
    this.fileListSubscription = this.yamcs.yamcsClient.createRemoteFileListSubscription({
      instance: this.yamcs.instance!,
      serviceName: this.service.name,
    }, fileList => {
      if (fileList.destination == this.form.get('remoteEntity')!.value) {
        const currentFolder: string = this.remoteSelector.currentPrefix$.value || '';
        if (fileList.remotePath == currentFolder) {
          this.remoteSelector.setFolderContent(currentFolder, fileList);
          this.lastFileListTime$.next(fileList.listTime);
        }
      }
    });

    // Setup forms
    this.form = formBuilder.group({
      localFilenames: ['', []],
      remoteFilenames: ['', []],
      localEntity: [firstSource, Validators.required],
      remoteEntity: [firstDestination, Validators.required],
      reliable: [true, []],
    });

    // Subscribe to some form variables to determine enabled state of buttons
    this.form.get('localFilenames')?.valueChanges.subscribe((value: any) => {
      this.updateButtonStates(value, this.form.get('remoteFilenames')?.value, this.form.get('remoteFilenames')?.value);
    });
    this.form.get('remoteFilenames')?.valueChanges.subscribe((value: any) => {
      this.updateButtonStates(this.form.get('localFilenames')?.value, value, this.form.get('remoteFilenames')?.value);
    });

    // If a new destination is selected -> display cached file list if any
    this.form.get('remoteEntity')?.valueChanges.subscribe((dest: any) => {
      // New destination selected -> Go to root folder
      this.getFileList(dest, '');
    });

    // Show most recent file list
    this.getFileList(firstDestination, '');
  }

  // Called when user selects a bucket
  selectBucket(bucket: Bucket) {
    this.selectedBucket$.next(bucket);
  }

  private updateButtonStates(localFiles: string, remoteFile: string, textfieldPath: string) {
    this.isDownloadEnabled = this.service.capabilities.download && this.selectedBucket$.value! && remoteFile != '' && this.form.valid;
    this.isUploadEnabled = this.service.capabilities.upload && localFiles != '' && this.form.valid;
  }

  // Returns remote folder path, ready to concatenate a file name
  private getSelectedRemoteFolderPath() {
    const items = this.remoteBreadcrumb$.value!;
    return items.length != 0 ? items[items.length - 1].prefix + '/' : '';
  }

  // Returns local folder path
  private getSelectedLocalFolderPath() {
    const items = this.breadcrumb$.value!;
    return items.length != 0 ? items[items.length - 1].prefix + '/' : '';
  }

  async startDownload() {
    const objectNames: string[] = this.form.get('remoteFilenames')?.value.split('|');
    if (!objectNames[0]) {
      return;
    }

    const promises = objectNames.map((name) => {
      // Get file names and append it to selected folders. // TODO: almost duplicate of Upload
      name = name.trim();
      const remoteFolderPath = this.getSelectedRemoteFolderPath();
      const remoteFileName = name.startsWith(remoteFolderPath) ? name.replace(remoteFolderPath, "") : name;
      const remotePath = remoteFolderPath + remoteFileName;

      const localFilenames: string = this.form.get("localFilenames")!.value.trim();
      if (localFilenames.includes("|")) {
        console.error(`Cannot download file "${remotePath}" to multiple destination filenames: ${localFilenames}`);
        return Promise.reject(`Cannot download file "${remotePath}" to multiple destination filenames: ${localFilenames}`);
      }
      const localFolderPath = this.getSelectedLocalFolderPath();
      const localFilename = localFilenames.startsWith(localFolderPath) ? localFilenames.replace(localFolderPath, "") : localFilenames;
      const localPath = localFolderPath + (localFilename ? localFilename : remoteFileName);

      // Start transfer
      return this.yamcs.yamcsClient.createFileTransfer(this.yamcs.instance!, this.service.name, {
        direction: 'DOWNLOAD',
        bucket: this.selectedBucket$.value!.name,
        objectName: localPath,
        remotePath: remotePath,
        // TODO: rename source-destination to local and remote
        source: this.form.get('remoteEntity')!.value,
        destination: this.form.get('localEntity')!.value,
        downloadOptions: {
          reliable: this.form.get('reliable')!.value
        }
      });
    });

    // Collect combined success/failure result
    let anyError: any;
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

  async startUpload() {
    const objectNames: string[] = this.form.get('localFilenames')?.value.trim().split('|');
    if (!objectNames[0]) {
      return;
    }

    const promises = objectNames.map((name) => {
      // Get file names and append it to selected folders.
      name = name.trim();
      const localFolderPath = this.getSelectedLocalFolderPath();
      const localFileName = name.startsWith(localFolderPath) ? name.replace(localFolderPath, "") : name;
      const localPath = localFolderPath + localFileName;

      const remoteFilenames: string = this.form.get("remoteFilenames")!.value.trim();
      if (remoteFilenames.includes("|")) {
        console.error(`Cannot upload file "${localPath}" to multiple destination filenames: ${remoteFilenames}`);
        return Promise.reject(`Cannot upload file "${localPath}" to multiple destination filenames: ${remoteFilenames}`);
      }
      const remoteFolderPath = this.getSelectedRemoteFolderPath();
      const remoteFilename = remoteFilenames.startsWith(remoteFolderPath) ? remoteFilenames.replace(remoteFolderPath, "") : remoteFilenames;
      const remotePath = remoteFolderPath + (remoteFilename ? remoteFilename : localFileName);

      // Start transfer
      return this.yamcs.yamcsClient.createFileTransfer(this.yamcs.instance!, this.service.name, {
        direction: 'UPLOAD',
        bucket: this.selectedBucket$.value!.name,
        objectName: localPath,
        remotePath: remotePath,
        source: this.form.get('localEntity')!.value,
        destination: this.form.get('remoteEntity')!.value,
        uploadOptions: {
          reliable: this.form.get('reliable')!.value
        }
      });
    });

    // Collect combined success/failure result
    let anyError: any;
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

  requestFileList() {
    const currentFolder: string = this.remoteSelector.currentPrefix$.value || '';
    this.yamcs.yamcsClient.requestFileList(this.yamcs.instance!, this.service.name, {
      source: this.form.value['localEntity'],
      destination: this.form.value['remoteEntity'],
      remotePath: currentFolder,
      reliable: this.form.value['reliable']
    });
  }

  getFileList(dest: string, prefix: string) {
    if (this.service.capabilities.fileList) {
      this.yamcs.yamcsClient.getFileList(this.yamcs.instance!, this.service.name, {
        source: this.form.value['localEntity'],
        destination: dest,
        remotePath: prefix,
        reliable: this.form.value['reliable']
      }).then(fileList => {
        this.remoteSelector.setFolderContent(prefix, fileList);
        this.lastFileListTime$.next(fileList.listTime);
      });
    }
  }

  updateLocalBreadcrumb(prefix: string | null) {
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

  // Called when breadcrumb is selected
  changeLocalPrefix(prefix: string) {
    if (prefix) {
      prefix = prefix + '/';
    }
    this.objectSelector.changePrefix(prefix);
  }

  // Called when a folder is selected in the object selector
  updateRemoteBreadcrumb(prefix: string | null) {
    if (!prefix) {
      this.remoteBreadcrumb$.next([]);
      this.getFileList(this.form.value['remoteEntity'], '');
      return;
    }

    // Remove trailing slash
    const strippedPrefix = prefix.endsWith('/') ? prefix.slice(0, -1) : prefix;

    const items: BreadcrumbItem[] = [];
    const parts = strippedPrefix.split('/');
    for (let i = 0; i < parts.length; i++) {
      items.push({
        name: parts[i],
        prefix: parts.slice(0, i + 1).join('/'),
      });
    }
    this.remoteBreadcrumb$.next(items);

    // Get most recent folder content and update object selector.
    this.getFileList(this.form.value['remoteEntity'], prefix);
  }

  // Called when breadcrumb is selected
  changeRemotePrefix(prefix: string) {
    if (prefix) {
      prefix = prefix + '/';
    }
    this.remoteSelector.changePrefix(prefix);
  }

  ngOnDestroy() {
    if (this.fileListSubscription) {
      this.fileListSubscription.cancel();
    }
  }
}

interface BreadcrumbItem {
  name: string;
  prefix: string;
}
