import { Component, ElementRef, Inject, ViewChild } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
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
    private dialogRef: MatDialogRef<CreateStackDialog>,
    formBuilder: UntypedFormBuilder,
    yamcs: YamcsService,
    configService: ConfigService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    this.bucket = configService.getConfig().stackBucket;
    this.storageClient = yamcs.createStorageClient();
    this.filenameForm = formBuilder.group({
      name: ['', [Validators.required]],
      format: ['json', [Validators.required]]
    });
  }

  save() {
    const format: "json" | "xml" = this.filenameForm.get('format')!.value;
    const name: string = this.filenameForm.get('name')!.value.trim() + '.' + format;

    let path = this.data.path;
    if (path.startsWith('/')) {
      path = path.substring(1);
    }
    const fullPath = path ? path + '/' + name : name;
    const objectName = this.data.prefix + fullPath;

    const file = format === 'xml' ? new StackFormatter([]).toXML() : new StackFormatter([]).toJSON();
    const b = new Blob([file], { type: 'application/' + format });
    this.storageClient.uploadObject('_global', this.bucket, objectName, b).then(() => {
      this.dialogRef.close(fullPath);
    });
  }
}
