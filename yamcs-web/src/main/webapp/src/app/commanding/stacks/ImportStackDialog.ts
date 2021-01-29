import { Component, ElementRef, Inject, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { BehaviorSubject } from 'rxjs';
import { StorageClient } from '../../client';
import { MessageService } from '../../core/services/MessageService';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  selector: 'app-import-stack-dialog',
  templateUrl: './ImportStackDialog.html',
})
export class ImportStackDialog {

  formGroup: FormGroup;

  @ViewChild('files')
  filesInput: ElementRef;

  uploading$ = new BehaviorSubject<boolean>(false);

  private storageClient: StorageClient;

  constructor(
    private dialogRef: MatDialogRef<ImportStackDialog>,
    private messageService: MessageService,
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

    const files: { [key: string]: File; } = this.filesInput.nativeElement.files;

    const uploadPromises = [];
    this.uploading$.next(true);
    for (const key in files) {
      if (!isNaN(parseInt(key, 10))) {
        const file = files[key];
        const fullPath = path ? path + '/' + file.name : file.name;
        const objectName = this.data.prefix + fullPath;
        const promise = this.storageClient.uploadObject('_global', 'stacks', objectName, file);
        uploadPromises.push(promise);
      }
    }

    Promise.all(uploadPromises).then(() => {
      this.uploading$.next(false);
      this.dialogRef.close(true);
    }).catch(err => {
      this.messageService.showError(err);
      this.uploading$.next(false);
      this.dialogRef.close(true);
    });
  }
}
