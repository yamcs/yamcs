import { Component, Inject, OnDestroy, ViewChild } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableDataSource } from '@angular/material/table';
import { Bucket, FileTransferOption, FileTransferService, MessageService, PreferenceStore, RemoteFileListSubscription, StorageClient, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { ObjectSelector } from '../../shared/object-selector/object-selector.component';
import { FileActionRequest, RemoteFileSelectorComponent } from '../remote-file-selector/remote-file-selector.component';

@Component({
  standalone: true,
  selector: 'app-transfer-file-dialog',
  templateUrl: './transfer-file-dialog.component.html',
  styleUrl: './transfer-file-dialog.component.css',
  imports: [
    ObjectSelector,
    WebappSdkModule,
    RemoteFileSelectorComponent,
  ],
})
export class TransferFileDialogComponent implements OnDestroy {
  public isDownloadEnabled = false;
  public isUploadEnabled = false;
  form: UntypedFormGroup;
  readonly service: FileTransferService;
  private storageClient: StorageClient;
  dataSource = new MatTableDataSource<Bucket>();

  displayedColumns = ['name'];

  private prefPrefix = 'filetransfer.';

  showBucketSize$;

  selectedBucket$ = new BehaviorSubject<Bucket | null>(null);
  breadcrumb$ = new BehaviorSubject<BreadcrumbItem[]>([]);
  remoteBreadcrumb$ = new BehaviorSubject<BreadcrumbItem[]>([]);
  lastFileListTime$ = new BehaviorSubject<string>("");
  lastFileListState$ = new BehaviorSubject<string | undefined>(undefined);

  private fileListSubscription: RemoteFileListSubscription;

  optionsMapping = new Map<FileTransferOption, string>();

  readonly DROPDOWN_SUFFIX = "_Dropdown";
  readonly CUSTOM_OPTION_VALUE = "_CUSTOM_OPTION_";

  @ViewChild('objectSelector') set content(selector: ObjectSelector) {
    if (!this.objectSelector) {
      if (!selector) {
        return;
      }
      this.objectSelector = selector;
    }

    this.changeLocalPrefix(this.localDirectory$.value);
  }
  objectSelector: ObjectSelector;
  private localDirectory$;

  @ViewChild('remoteSelector')
  remoteSelector: RemoteFileSelectorComponent;

  constructor(
    private dialogRef: MatDialogRef<TransferFileDialogComponent>,
    readonly yamcs: YamcsService,
    formBuilder: UntypedFormBuilder,
    private messageService: MessageService,
    private preferenceStore: PreferenceStore,
    private snackBar: MatSnackBar,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    this.service = data.service;
    this.prefPrefix += this.service.name + ".";
    this.showBucketSize$ = this.addPreference$('showBucketSize', false);
    this.localDirectory$ = this.addPreference$('localDirectory', "");
    const firstLocalEntity = this.service.localEntities && this.service.localEntities.length ? this.service.localEntities[0].name : '';
    const firstRemoteEntity = this.service.remoteEntities && this.service.remoteEntities.length ? this.service.remoteEntities[0].name : '';
    const localEntity$ = this.addPreference$('localEntity', firstLocalEntity);
    const remoteEntity$ = this.addPreference$('remoteEntity', firstRemoteEntity);

    this.storageClient = yamcs.createStorageClient();
    this.storageClient.getBuckets().then(buckets => {
      this.dataSource.data = buckets || [];
      if (buckets) {
        const bucketPref$ = this.addPreference$('selectedBucket', buckets[0].name);
        const bucket = buckets.find(bucket => bucket.name === bucketPref$.value);
        this.selectBucket(bucket || buckets[0]);
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
          this.lastFileListState$.next(fileList.state);
        }
      }
    });

    // Prepare form control names for custom options
    let controlNames: { [key: string]: any; } = {};

    this.service.transferOptions?.forEach(
      (option, index) => {
        let name = this.getControlName(option, index);
        this.optionsMapping.set(option, name);

        if (option.type === "BOOLEAN") {
          const optionPref$ = this.addPreference$("options." + option.name, option.default?.toLowerCase() === "true");
          controlNames[name] = [optionPref$.value, []];
        } else {
          const optionPref$ = this.addPreference$("options." + option.name, option.default || '');
          let inValues = option.values && option.values.find(item => item.value === optionPref$.value);

          controlNames[name] = [inValues == null ? optionPref$.value : "", []];
          controlNames[name + this.DROPDOWN_SUFFIX] = [inValues != null ? optionPref$.value : this.CUSTOM_OPTION_VALUE, []];
        }
      }
    );

    // Setup forms
    this.form = formBuilder.group({
      localFilenames: ['', []],
      remoteFilenames: ['', []],
      localEntity: [localEntity$.value, this.service.localEntities && this.service.localEntities.length && Validators.required],
      remoteEntity: [remoteEntity$.value, this.service.remoteEntities && this.service.remoteEntities.length && Validators.required],
      ...controlNames
    });

    // Subscribe to some form variables to determine enabled state of buttons
    this.form.get('localFilenames')?.valueChanges.subscribe((value: any) => {
      this.updateButtonStates(value, this.form.get('remoteFilenames')?.value, this.form.get('remoteFilenames')?.value);
    });
    this.form.get('remoteFilenames')?.valueChanges.subscribe((value: any) => {
      this.updateButtonStates(this.form.get('localFilenames')?.value, value, this.form.get('remoteFilenames')?.value);
    });

    // Update entity user preference
    this.form.get('localEntity')?.valueChanges.subscribe((entity: any) => {
      this.setPreferenceValue("localEntity", entity);
    });

    // If a new destination is selected -> display cached file list if any
    this.form.get('remoteEntity')?.valueChanges.subscribe((entity: any) => {
      this.setPreferenceValue("remoteEntity", entity);
      // New destination selected -> Go to root folder
      this.getFileList(entity, '');
    });

    // Save option preferences
    this.form.valueChanges.subscribe(async _ => {
      this.optionsMapping.forEach((controlName, option) => {
        const dropDownValue = this.form.get(controlName + this.DROPDOWN_SUFFIX)?.value;
        let value = dropDownValue == null || dropDownValue === this.CUSTOM_OPTION_VALUE ? this.form.get(controlName)?.value : dropDownValue;

        switch (option.type) {
          case "BOOLEAN":
            this.setPreferenceValue("options." + option.name, String(value) === "true");
            break;
          case "DOUBLE":
          case "STRING":
            this.setPreferenceValue("options." + option.name, value != null ? value : "");
        }
      });
    });

    // Show most recent file list
    const remoteDirectory$ = this.addPreference$('remoteDirectory', "");
    this.getFileList(remoteEntity$.value, remoteDirectory$.value || '');
  }

  getControlName(option: FileTransferOption, index: number) {
    return "option" + index + option.name.replace(/\s/g, "");
  }

  // Called when user selects a bucket
  selectBucket(bucket: Bucket) {
    this.selectedBucket$.next(bucket);
    this.setPreferenceValue("selectedBucket", bucket.name);
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
        options: this.getTransferOptions()
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
      options: this.getTransferOptions()
    });
  }

  getFileList(dest: string, prefix: string) {
    if (this.service.capabilities.fileList) {
      this.yamcs.yamcsClient.getFileList(this.yamcs.instance!, this.service.name, {
        source: this.form.value['localEntity'],
        destination: dest,
        remotePath: prefix,
        options: this.getTransferOptions()
      }).then(fileList => {
        this.remoteSelector.setFolderContent(prefix, fileList);
        this.setPreferenceValue("remoteDirectory", prefix);
        this.lastFileListTime$.next(fileList.listTime);
        this.lastFileListState$.next(fileList.state);
      });
    }
  }

  private getTransferOptions() {
    return this.service.transferOptions?.reduce((options, option) => {
      const controlName = this.optionsMapping.get(option);
      if (!controlName) {
        return options;
      }

      const dropDownValue = this.form.get(controlName + this.DROPDOWN_SUFFIX)?.value;
      let value = dropDownValue == null || dropDownValue === this.CUSTOM_OPTION_VALUE ? this.form.get(controlName)?.value : dropDownValue;

      if (option.type === "BOOLEAN" && typeof value !== "boolean") {
        value = String(value).toLowerCase() === "true";
      } else if (option.type === "DOUBLE" && typeof value !== "number") {
        value = Number(value);
      } else if (option.type === "STRING" && typeof value !== "string") {
        value = String(value);
      }

      return {
        ...options,
        [option.name]: value,
      };
    }, {});
  }

  updateLocalBreadcrumb(prefix: string | null) {
    if (!prefix) {
      this.breadcrumb$.next([]);
      this.setPreferenceValue("localDirectory", "");
      return;
    }

    if (prefix.endsWith('/')) {
      prefix = prefix.substring(0, prefix.length - 1);
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
    this.setPreferenceValue("localDirectory", prefix);
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

  clearSelection(event: MouseEvent, selector: ObjectSelector | RemoteFileSelectorComponent) {
    if (event.currentTarget === event.target) {
      selector.clearSelection();
    }
  }

  onActionRequest(request: FileActionRequest) {
    this.snackBar.open(`Running '${request.action.label}' ...`, undefined, {
      horizontalPosition: 'end',
    });
    const remoteEntity = this.form.value['remoteEntity'];
    this.yamcs.yamcsClient.runFileAction(
      this.yamcs.instance!, this.service.name, {
      remoteEntity,
      file: request.file,
      action: request.action.id,
    }).then(() => {
      this.snackBar.open(`'${request.action.label}' successful`, undefined, {
        duration: 3000,
        horizontalPosition: 'end',
      });
    }).catch(err => {
      this.messageService.showError(err);
      this.snackBar.open(`'${request.action.label}' failed`, undefined, {
        duration: 3000,
        horizontalPosition: 'end',
      });
    });
  }

  toggleBucketSize() {
    this.setPreferenceValue("showBucketSize", !this.showBucketSize$.value);
  }

  private addPreference$<Type>(key: string, defaultValue: Type) {
    return this.preferenceStore.addPreference$(this.prefPrefix + key, defaultValue);
  }

  private setPreferenceValue<Type>(key: string, value: Type) {
    this.preferenceStore.setValue(this.prefPrefix + key, value);
  }

  ngOnDestroy() {
    this.fileListSubscription?.cancel();
  }
}

interface BreadcrumbItem {
  name: string;
  prefix: string;
}
