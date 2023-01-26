import { Component, Inject, OnDestroy, ViewChild } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatTableDataSource } from '@angular/material/table';
import { BehaviorSubject } from 'rxjs';
import { Bucket, FileTransferOption, FileTransferService, RemoteFileListSubscription, StorageClient } from '../client';
import { MessageService } from '../core/services/MessageService';
import { YamcsService } from '../core/services/YamcsService';
import { ObjectSelector } from '../shared/forms/ObjectSelector';
import { RemoteFileSelector } from './RemoteFileSelector';

@Component({
  selector: 'app-download-file-dialog',
  templateUrl: './TransferFileDialog.html',
  styleUrls: ['./TransferFileDialog.css'],
})
export class TransferFileDialog implements OnDestroy {
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
  lastFileListTime$ = new BehaviorSubject<string>("");

  private fileListSubscription: RemoteFileListSubscription;

  spacesRegex = /\s/g;
  optionsMapping = new Map<FileTransferOption, string>();

  @ViewChild('objectSelector')
  objectSelector: ObjectSelector;

  @ViewChild('remoteSelector')
  remoteSelector: RemoteFileSelector;

  constructor(
    private dialogRef: MatDialogRef<TransferFileDialog>,
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

    // Prepare form control names for custom options
    let controlNames: { [key: string]: any; } = {};
    this.service.transferOptions?.forEach(
      (option, index) => {
        let name = "option" + index + option.name.toLowerCase().replace(this.spacesRegex, "");
        this.optionsMapping.set(option, name);

        let defaultValue = // String default
          option.stringOption ? option.stringOption.default ||
            (option.stringOption.values && option.stringOption.values.length && option.stringOption.values[0].value) :
            // Mumber default
            option.numberOption ? (option.numberOption.default ? (option.numberOption.default.double != null ? option.numberOption.default.double : option.numberOption.default.int) :
              (option.numberOption.values && option.numberOption.values.length && (option.numberOption.values[0].double != null ? option.numberOption.values[0].double : option.numberOption.values[0].int))) :
              // Boolean default
              option.booleanOption ? option.booleanOption.value : false;

        controlNames[name] = [defaultValue, []];
      }
    );

    // Setup forms
    this.form = formBuilder.group({
      localFilenames: ['', []],
      remoteFilenames: ['', []],
      localEntity: [firstSource, Validators.required],
      remoteEntity: [firstDestination, Validators.required],
      reliable: [true, []],
      ...controlNames
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
    return this.startTransfer(
      this.form.get("remoteEntity")?.value,
      this.form.get("localEntity")?.value,
      this.form.get('remoteFilenames')?.value,
      this.form.get('localFilenames')?.value,
      this.getSelectedRemoteFolderPath(),
      this.getSelectedLocalFolderPath(),
      "DOWNLOAD"
    );
  }

  async startUpload() {
    return this.startTransfer(
      this.form.get("localEntity")?.value,
      this.form.get("remoteEntity")?.value,
      this.form.get('localFilenames')?.value,
      this.form.get('remoteFilenames')?.value,
      this.getSelectedLocalFolderPath(),
      this.getSelectedRemoteFolderPath(),
      "UPLOAD"
    );
  }

  async startTransfer(sourceEntity: string, destinationEntity: string, sourceFilenames: string, destinationFilenames: string, sourceFolderPath: string, destinationFolderPath: string, direction: "UPLOAD" | "DOWNLOAD") {
    const objectNames: string[] = sourceFilenames.trim().split('|');
    if (!objectNames[0]) {
      return;
    }

    // FileTransferOptions
    const options = this.service.transferOptions?.map(option => {
      let formControlName = this.optionsMapping.get(option);
      if (!formControlName) {
        return;
      }
      let value = this.form.get(formControlName)?.value;
      let newOption: FileTransferOption = {
        name: option.name
      };
      if (option.stringOption) {
        newOption.stringOption = { values: [{ value: value }] };
      } else if (option.numberOption) {
        newOption.numberOption = {
          values: [Number.isInteger(value) ? { int: value } : { double: value }]
        };
      } else if (option.booleanOption) {
        newOption.booleanOption = { value: value };
      } else {
        return;
      }
      return newOption;
    }).filter((option): option is FileTransferOption => !!option); // Filter undefined

    const localFolderPath = this.getSelectedLocalFolderPath();
    const remoteFolderPath = this.getSelectedRemoteFolderPath();

    const promises = objectNames.map((name) => {
      // Get file names and append it to selected folders.
      let paths;
      try {
        paths = this.getTransferPaths(name, destinationFilenames, sourceFolderPath, destinationFolderPath, direction);
      } catch (error: any) {
        this.messageService.showError(error);
        throw error;
      }

      if (direction == "DOWNLOAD") {
        paths = paths.reverse();
      }

      // Start transfer
      return this.yamcs.yamcsClient.createFileTransfer(this.yamcs.instance!, this.service.name, {
        direction: direction,
        bucket: this.selectedBucket$.value!.name,
        objectName: paths[0],
        remotePath: paths[1],
        source: sourceEntity,
        destination: destinationEntity,
        options: options
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

  getTransferPaths(sourceFilename: string, destinationFilenames: string, sourceFolderPath: string, destinationFolderPath: string, direction: "UPLOAD" | "DOWNLOAD"): [string, string] {
    sourceFilename = sourceFilename.trim();
    sourceFilename = sourceFilename.startsWith(sourceFolderPath) ? sourceFilename.replace(sourceFolderPath, "") : sourceFilename;
    const sourcePath = sourceFolderPath + sourceFilename;

    let destinationPath = sourceFilename;
    if ((direction === "UPLOAD" && this.service.capabilities.remotePath) || direction === "DOWNLOAD") {
      destinationFilenames = destinationFilenames.trim();
      if (destinationFilenames.includes("|")) {
        console.error(`Cannot ${direction.toLowerCase()} file "${sourcePath}" to multiple destination filenames: ${destinationFilenames}`);
        throw new Error(`Cannot ${direction.toLowerCase()} file "${sourcePath}" to multiple destination filenames: ${destinationFilenames}`);
      }
      const destinationFilename = destinationFilenames.startsWith(destinationFolderPath) ? destinationFilenames.replace(destinationFolderPath, "") : destinationFilenames;
      destinationPath = destinationFolderPath + (destinationFilename ? destinationFilename : sourceFilename);
    }

    return [sourcePath, destinationPath];
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

  clearSelection(event: MouseEvent, selector: ObjectSelector | RemoteFileSelector) {
    if (event.currentTarget === event.target) {
      selector.clearSelection();
    }
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
