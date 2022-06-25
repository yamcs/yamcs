import { ChangeDetectorRef, Component, ElementRef, Inject, ViewChild } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { StorageClient } from '../../client';
import { ConfigService, WebsiteConfig } from '../../core/services/ConfigService';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  selector: 'app-create-display-dialog',
  templateUrl: './CreateDisplayDialog.html',
})
export class CreateDisplayDialog {

  page = 1;

  // Page 1
  typeForm: UntypedFormGroup;

  // Page 2
  filenameForm: UntypedFormGroup;

  @ViewChild('filename')
  filenameInput: ElementRef;

  private config: WebsiteConfig;
  private storageClient: StorageClient;

  constructor(
    private dialogRef: MatDialogRef<CreateDisplayDialog>,
    formBuilder: UntypedFormBuilder,
    yamcs: YamcsService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
    private changeDetector: ChangeDetectorRef,
    configService: ConfigService,
  ) {
    this.config = configService.getConfig();
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

    const display = {
      scroll: false,
      parameters: [],
    };
    const b = new Blob([JSON.stringify(display, undefined, 2)], {
      type: 'application/json'
    });
    const bucketName = this.config.displayBucket;
    const objectName = this.data.prefix + fullPath;
    this.storageClient.uploadObject('_global', bucketName, objectName, b).then(() => {
      this.dialogRef.close(fullPath);
    });
  }
}
