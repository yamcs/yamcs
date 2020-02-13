import { Component, ElementRef, Inject, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { StorageClient } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  selector: 'app-create-stack-dialog',
  templateUrl: './CreateStackDialog.html',
})
export class CreateStackDialog {

  filenameForm: FormGroup;

  @ViewChild('filename')
  filenameInput: ElementRef;

  private storageClient: StorageClient;

  constructor(
    private dialogRef: MatDialogRef<CreateStackDialog>,
    formBuilder: FormBuilder,
    yamcs: YamcsService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    this.storageClient = yamcs.createStorageClient();
    this.filenameForm = formBuilder.group({
      path: [data.path, Validators.required],
      name: ['', [Validators.required]],
    });
  }

  save() {
    let path: string = this.filenameForm.get('path')!.value.trim();
    const name: string = this.filenameForm.get('name')!.value.trim() + '.xml';

    // Full path should not have a leading slash

    if (path.startsWith('/')) {
      path = path.substring(1);
    }
    if (path.endsWith('/')) {
      path = path.substring(0, path.length - 1);
    }
    const fullPath = path ? path + '/' + name : name;

    const display = {
      scroll: false,
      parameters: [],
    };
    const b = new Blob([JSON.stringify(display, undefined, 2)], {
      type: 'application/xml'
    });
    this.storageClient.uploadObject('_global', 'stacks', fullPath, b).then(() => {
      this.dialogRef.close(fullPath);
    });
  }
}
