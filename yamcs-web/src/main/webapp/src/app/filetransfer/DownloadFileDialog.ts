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
  filesForm: UntypedFormGroup;
  optionsForm: UntypedFormGroup;
  service: FileTransferService;
  private storageClient: StorageClient;
  dataSource = new MatTableDataSource<Bucket>();

  displayedColumns = ['name'];

  selectedBucket$ = new BehaviorSubject<Bucket | null>(null);
  breadcrumb$ = new BehaviorSubject<BreadcrumbItem[]>([]);
  remoteBreadcrumb$ = new BehaviorSubject<BreadcrumbItem[]>([]);
  lastFileListTime$ = new BehaviorSubject<String>('-');

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
    });

    // Subscribe to remote file list updates
    this.fileListSubscription = this.yamcs.yamcsClient.createRemoteFileListSubscription({
      instance: this.yamcs.instance!,
      serviceName: this.service.name,
    }, fileList => {
      if (fileList.destination == this.optionsForm.get('destination')!.value) {
        const currentFolder: string = this.remoteSelector.currentPrefix$.value || '';
        if (fileList.remotePath == currentFolder) {
          this.remoteSelector.setFolderContent(currentFolder, fileList);
          this.lastFileListTime$.next(fileList.listTime);
        }
      }
    });

    // Setup forms
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

    // Subscribe to some form variables to determine enabled state of buttons
    this.filesForm.get('object')?.valueChanges.subscribe((value: any) => {
      this.updateButtonStates(value, this.filesForm.get('remoteFile')!.value, this.optionsForm.get('remotePath')!.value);
    });
    this.filesForm.get('remoteFile')?.valueChanges.subscribe((value: any) => {
      this.updateButtonStates(this.filesForm.get('object')!.value, value, this.optionsForm.get('remotePath')!.value);
    });
    this.optionsForm.get('remotePath')?.valueChanges.subscribe((value: any) => {
      this.updateButtonStates(this.filesForm.get('object')!.value, this.filesForm.get('remoteFile')!.value, value);
    });

    // If a new destination is selected -> display cached file list if any
    this.optionsForm.get('destination')?.valueChanges.subscribe((dest: any) => {
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
    var remotePath = '';
    if (this.service.capabilities.fileList && remoteFile) {
      remotePath = remoteFile;
    } else if (this.service.capabilities.remotePath && textfieldPath) {
      remotePath = textfieldPath;
    }
    this.isDownloadEnabled = this.service.capabilities.download && this.selectedBucket$.value! && remotePath != '' && this.optionsForm.valid;

    this.isUploadEnabled = this.service.capabilities.upload && localFiles != '' && this.optionsForm.valid;
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

  startDownload() {
    // Get the full file name path from the text field, or the selected file (with full path).
    // Must have either a selected file from the object selector or a file from the text field.
    // Capabilities are taken into account.
    var remotePath: string;
    const formTextInputPath = this.optionsForm.get('remotePath')!.value;
    const formSelectedFile = this.filesForm.get('remoteFile')!.value;
    if (this.service.capabilities.remotePath && formTextInputPath) {
      remotePath = formTextInputPath;
    } else if (this.service.capabilities.fileList && formSelectedFile) {
      remotePath = formSelectedFile;
    } else {
      return;
    }

    // Get file name from remote and append it to selected bucket folder.
    const remoteParts = remotePath.split('/');
    const localPath = this.getSelectedLocalFolderPath() + remoteParts[remoteParts.length - 1];

    // Start transfer
    this.yamcs.yamcsClient.createFileTransfer(this.yamcs.instance!, this.service.name, {
      direction: 'DOWNLOAD',
      bucket: this.selectedBucket$.value!.name,
      objectName: localPath,
      remotePath: remotePath,
      source: this.optionsForm.get('source')!.value,
      destination: this.optionsForm.get('destination')!.value,
      uploadOptions: {
        reliable: this.optionsForm.get('reliable')!.value
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
      // Get the full file name path from the text field, or the selected file (with full path).
      // Must have either a selected file from the object selector or a file from the text field.
      // Capabilities are taken into account.
      var remotePath: string;
      const formTextInputPath = this.optionsForm.get('remotePath')!.value;
      if (this.service.capabilities.remotePath && formTextInputPath) {
        // Use text field as remote path (overrides remote file selector)
        remotePath = formTextInputPath;
      } else if (this.service.capabilities.fileList) {
        // Use selected folder (not selected file) as remote path, and append local object name
        const parts = name.split('/');
        remotePath = this.getSelectedRemoteFolderPath() + parts[parts.length - 1];
      } else {
        // Use object name as remote file name, with no remote folder
        const parts = name.split('/');
        remotePath = parts[parts.length - 1];
      }

      // Start transfer
      this.yamcs.yamcsClient.createFileTransfer(this.yamcs.instance!, this.service.name, {
        direction: 'UPLOAD',
        bucket: this.selectedBucket$.value!.name,
        objectName: name,
        remotePath: remotePath,
        source: this.optionsForm.get('source')!.value,
        destination: this.optionsForm.get('destination')!.value,
        uploadOptions: {
          reliable: this.optionsForm.get('reliable')!.value
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
      remotePath: currentFolder,
      destination: this.optionsForm.value['destination']
    });
  }

  getFileList(dest: string, prefix: string) {
    if (this.service.capabilities.fileList) {
      this.yamcs.yamcsClient.getFileList(this.yamcs.instance!, this.service.name, {
        remotePath: prefix,
        destination: dest
      }).then(fileList => {
        this.remoteSelector.setFolderContent(prefix, fileList);
        this.lastFileListTime$.next(fileList.listTime);
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
