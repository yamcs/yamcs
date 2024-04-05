import { ChangeDetectorRef, Component, ElementRef, Inject, ViewChild } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { ConfigService, StorageClient, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-create-display-dialog',
  templateUrl: './create-display-dialog.component.html',
  imports: [
    WebappSdkModule,
  ],
})
export class CreateDisplayDialogComponent {

  page = 1;

  // Page 1
  typeForm: UntypedFormGroup;

  // Page 2
  filenameForm: UntypedFormGroup;

  @ViewChild('filename')
  filenameInput: ElementRef;

  private storageClient: StorageClient;

  constructor(
    private dialogRef: MatDialogRef<CreateDisplayDialogComponent>,
    formBuilder: UntypedFormBuilder,
    yamcs: YamcsService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
    private changeDetector: ChangeDetectorRef,
    private configService: ConfigService,
  ) {
    this.storageClient = yamcs.createStorageClient();
    this.typeForm = formBuilder.group({
      type: ['par', Validators.required],
    });
    this.filenameForm = formBuilder.group({
      path: [data.path, Validators.required],
      name: ['', [Validators.required, Validators.pattern(/.*\.par/i)]],
    });
  }

  selectType() {
    const type = this.typeForm.value.type;
    this.filenameForm.get('name')!.setValue(`NewFile.${type}`);
    this.page = 2;

    // Ensure input is rendered before select
    this.changeDetector.detectChanges();
    this.filenameInput.nativeElement.select();
  }

  save() {
    let path: string = this.filenameForm.get('path')!.value.trim();
    const name: string = this.filenameForm.get('name')!.value.trim();

    // Full path should not have a leading slash

    if (path.startsWith('/')) {
      path = path.substring(1);
    }
    if (path.endsWith('/')) {
      path = path.substring(0, path.length - 1);
    }
    const fullPath = path ? path + '/' + name : name;

    const b = new Blob([JSON.stringify({
      "$schema": "https://yamcs.org/schema/parameter-table.schema.json",
      scroll: false,
      parameters: [],
    }, undefined, 2)], {
      type: 'application/json'
    });
    const bucketName = this.configService.getDisplayBucket();
    const objectName = this.data.prefix + fullPath;
    this.storageClient.uploadObject(bucketName, objectName, b).then(() => {
      this.dialogRef.close(fullPath);
    });
  }
}
