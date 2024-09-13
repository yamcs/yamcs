import { Component, ElementRef, Inject, ViewChild } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { ConfigService, StackFormatter, StorageClient, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-create-stack-dialog',
  templateUrl: './create-stack-dialog.component.html',
  imports: [
    WebappSdkModule,
  ],
})
export class CreateStackDialogComponent {

  filenameForm: UntypedFormGroup;

  @ViewChild('filename')
  filenameInput: ElementRef;

  private storageClient: StorageClient;
  private bucket: string;

  constructor(
    private dialogRef: MatDialogRef<CreateStackDialogComponent>,
    formBuilder: UntypedFormBuilder,
    yamcs: YamcsService,
    configService: ConfigService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    this.bucket = configService.getStackBucket();
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
