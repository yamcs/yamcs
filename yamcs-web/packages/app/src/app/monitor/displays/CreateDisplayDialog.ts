import { ChangeDetectorRef, Component, ElementRef, Inject, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  selector: 'app-create-display-dialog',
  templateUrl: './CreateDisplayDialog.html',
})
export class CreateDisplayDialog {

  page = 1;

  // Page 1
  typeForm: FormGroup;

  // Page 2
  filenameForm: FormGroup;

  @ViewChild('filename')
  filenameInput: ElementRef;

  constructor(
    private dialogRef: MatDialogRef<CreateDisplayDialog>,
    formBuilder: FormBuilder,
    private yamcs: YamcsService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
    private changeDetector: ChangeDetectorRef,
  ) {
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
    this.yamcs.getInstanceClient()!.uploadObject('displays', fullPath, b).then(() => {
      this.dialogRef.close(fullPath);
    });
  }
}
