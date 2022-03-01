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
      name: ['', [Validators.required]],
    });
  }

  save() {
    const name: string = this.filenameForm.get('name')!.value.trim() + '.xml';

    let path = this.data.path;
    if (path.startsWith('/')) {
      path = path.substring(1);
    }
    const fullPath = path ? path + '/' + name : name;
    const objectName = this.data.prefix + fullPath;

    const b = new Blob([], {
      type: 'application/xml'
    });
    this.storageClient.uploadObject('_global', 'stacks', objectName, b).then(() => {
      this.dialogRef.close(fullPath);
    });
  }
}
