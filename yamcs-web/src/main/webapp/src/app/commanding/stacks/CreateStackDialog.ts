import { Component, ElementRef, Inject, ViewChild } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { MAT_LEGACY_DIALOG_DATA, MatLegacyDialogRef } from '@angular/material/legacy-dialog';
import { StorageClient } from '../../client';
import { ConfigService } from '../../core/services/ConfigService';
import { YamcsService } from '../../core/services/YamcsService';
import { StackFormatter } from './StackFormatter';

@Component({
  selector: 'app-create-stack-dialog',
  templateUrl: './CreateStackDialog.html',
})
export class CreateStackDialog {

  filenameForm: UntypedFormGroup;

  @ViewChild('filename')
  filenameInput: ElementRef;

  private storageClient: StorageClient;
  private bucket: string;

  constructor(
    private dialogRef: MatLegacyDialogRef<CreateStackDialog>,
    formBuilder: UntypedFormBuilder,
    yamcs: YamcsService,
    configService: ConfigService,
    @Inject(MAT_LEGACY_DIALOG_DATA) readonly data: any,
  ) {
    this.bucket = configService.getConfig().stackBucket;
    this.storageClient = yamcs.createStorageClient();
    this.filenameForm = formBuilder.group({
      name: ['', [Validators.required]],
      format: ['ycs', [Validators.required]]
    });
  }

  save() {
    const format: "ycs" | "xml" = this.filenameForm.get('format')!.value;
    const name: string = this.filenameForm.get('name')!.value.trim() + '.' + format;

    let path = this.data.path;
    if (path.startsWith('/')) {
      path = path.substring(1);
    }
    const fullPath = path ? path + '/' + name : name;
    const objectName = this.data.prefix + fullPath;

    const file = format === 'xml' ? new StackFormatter([], {}).toXML() : new StackFormatter([], {}).toJSON();
    const type = (format === 'xml' ? 'application/xml' : 'application/json');
    const b = new Blob([file], { type });
    this.storageClient.uploadObject(this.bucket, objectName, b).then(() => {
      this.dialogRef.close(fullPath);
    });
  }
}
