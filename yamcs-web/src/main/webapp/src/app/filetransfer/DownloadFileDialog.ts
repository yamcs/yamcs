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
  selector: 'app-download-file-dialog',
  templateUrl: './DownloadFileDialog.html',
  styleUrls: ['./DownloadFileDialog.css'],
})
export class DownloadFileDialog {

  isDownloadEnabled = false
  isUploadEnabled = false
  filesForm: FormGroup;
  optionsForm: FormGroup;
  service: FileTransferService;
  private storageClient: StorageClient;
  dataSource = new MatTableDataSource<Bucket>();

  displayedColumns = ['name'];

  selectedBucket$ = new BehaviorSubject<Bucket | null>(null);
  breadcrumb$ = new BehaviorSubject<BreadcrumbItem[]>([]);
  remoteBreadcrumb$ = new BehaviorSubject<BreadcrumbItem[]>([]);

  @ViewChild('selector')
  objectSelector: ObjectSelector;

  @ViewChild('remoteSelector')
  remoteSelector: ObjectSelector;

  constructor(
    private dialogRef: MatDialogRef<DownloadFileDialog>,
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
    this.filesForm = formBuilder.group({
      object: ['', []],
      remoteFile: ['', []],
    });
    this.optionsForm = formBuilder.group({
      remotePath: ['', []],
      source: [firstSource, Validators.required],
      destination: [firstDestination, Validators.required],
      reliable: [true, []],
    });

    this.filesForm.valueChanges.subscribe(() => {
      this.updateButtonStates();
    });

    this.optionsForm.get('destination')!.valueChanges.subscribe(dest => {
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

  updateButtonStates() {
    var remotePath = '';
    if (this.service.capabilities.fileList && this.filesForm.value['remoteFile']) {
       remotePath = this.filesForm.value['remoteFile'];
    } else if (this.service.capabilities.remotePath && this.filesForm.value['remotePath']) {
       remotePath = this.filesForm.value['remotePath'];
    }
    this.isDownloadEnabled = this.service.capabilities.download && this.selectedBucket$.value! && remotePath != '' && this.optionsForm.valid;

    this.isUploadEnabled = this.service.capabilities.upload && this.filesForm.value['object'] && this.optionsForm.valid;
  }

  // Returns remote folder path
  private getSelectedRemoteFolderPath() {
    const items: BreadcrumbItem[] = this.remoteBreadcrumb$.value!;
    return items.length != 0 ? items[items.length - 1].prefix : '';
  }

  // Returns local folder path
  private getSelectedLocalFolderPath() {
    const items: BreadcrumbItem[] = this.breadcrumb$.value!;
    return items.length != 0 ? items[items.length - 1].prefix : '';
  }

  startDownload() {
    // Get the full file name path from the text field, or the selected file (with full path).
    // Must have either a selected file from the object selector or a file from the text field.
    // Capabilities are taken into account.
    var remotePath: string;
    if (this.service.capabilities.remotePath && this.filesForm.value['remotePath']) {
       remotePath = this.filesForm.value['remotePath'];
    } else if (this.service.capabilities.fileList && this.filesForm.value['remoteFile']) {
       remotePath = this.filesForm.value['remoteFile'];
    } else {
      return;
    }

    // Get file name from remote and store in selected bucket folder.
    var localPath: string = this.getSelectedLocalFolderPath();
    if (localPath) {
      localPath = localPath + '/';
    }
    const remoteParts: string[] = remotePath.split('/');
    localPath = localPath + remoteParts[remoteParts.length - 1];

    this.yamcs.yamcsClient.createFileTransfer(this.yamcs.instance!, this.service.name, {
      direction: 'DOWNLOAD',
      bucket: this.selectedBucket$.value!.name,
      objectName: localPath,
      remotePath: remotePath,
      source: this.optionsForm.value['source'],
      destination: this.optionsForm.value['destination'],
      uploadOptions: {
        reliable: this.optionsForm.value['reliable']
      }
    }).then(() => {
      this.dialogRef.close();
    });
  }

  async startUpload() {
    const objectNames: string[] = this.filesForm.value['object'].split('|');
    if (!objectNames[0]) {
      return;
    }

    const promises = objectNames.map((name) => {
      var remotePath: string;
      if (this.service.capabilities.remotePath && this.filesForm.value['remotePath']) {
        // Use text field as remote path (overrides remote file selector)
        remotePath = this.filesForm.value['remotePath'];
      } else if (this.service.capabilities.fileList) {
        // Use selected folder (not file) as remote path, and append object name
        const localParts: string[] = name.split('/');
        remotePath = this.getSelectedRemoteFolderPath() + '/' + localParts[localParts.length - 1];
      } else {
        // Use object name as remote file name, with no remote folder
        const localParts: string[] = name.split('/');
        remotePath = localParts[localParts.length - 1];
      }

      this.yamcs.yamcsClient.createFileTransfer(this.yamcs.instance!, this.service.name, {
        direction: 'UPLOAD',
        bucket: this.selectedBucket$.value!.name,
        objectName: name,
        remotePath: remotePath,
        source: this.optionsForm.value['source'],
        destination: this.optionsForm.value['destination'],
        uploadOptions: {
          reliable: this.optionsForm.value['reliable']
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
    this.yamcs.yamcsClient.requestFileList(this.yamcs.instance!, this.service.name, {
      remotePath: this.filesForm.value['remotePath'],
      destination: this.optionsForm.value['destination']
    })
  }

  getFileList(dest: string, prefix: string) {
    if (this.service.capabilities.fileList) {
      this.yamcs.yamcsClient.getFileList(this.yamcs.instance!, this.service.name, {
        remotePath: prefix,
        destination: dest
      }).then(response => {

        // For testing subfolders we set one subfolder:
        const num = prefix.split('/').length;
        const folderName = dest + ' Test Folder ' + num + '/';
        const fullFolderName = prefix + folderName;
        response.prefixes = [fullFolderName];

        this.remoteSelector.setFolderContent(prefix, response);
      });
    }
  }

  updateLocalBreadcrumb(prefix: string) {
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
  updateRemoteBreadcrumb(prefix: string) {
    if (!prefix) {
      this.remoteBreadcrumb$.next([]);
      this.getFileList(this.optionsForm.value['destination'], '');
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
    this.getFileList(this.optionsForm.value['destination'], prefix);
  }

  // Called when breadcrumb is selected
  changeRemotePrefix(prefix: string) {
    if (prefix) {
      prefix = prefix + '/';
    }
    this.remoteSelector.changePrefix(prefix);
  }
}

interface BreadcrumbItem {
  name: string;
  prefix: string;
}
