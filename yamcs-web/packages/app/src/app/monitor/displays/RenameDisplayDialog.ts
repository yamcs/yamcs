import { Component, Inject } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material';
import { YamcsService } from '../../core/services/YamcsService';
import { FilenamePipe } from '../../shared/pipes/FilenamePipe';

@Component({
  selector: 'app-rename-display-dialog',
  templateUrl: './RenameDisplayDialog.html',
})
export class RenameDisplayDialog {

  filenameForm: FormGroup;

  constructor(
    private dialogRef: MatDialogRef<RenameDisplayDialog>,
    formBuilder: FormBuilder,
    private yamcs: YamcsService,
    filenamePipe: FilenamePipe,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    const filename = filenamePipe.transform(this.data.name);
    this.filenameForm = formBuilder.group({
      name: [filename, [Validators.required]],
    });
  }

  async rename() {
    let prefix;
    const idx = this.data.name.lastIndexOf('/');
    if (idx !== -1) {
      prefix = this.data.name.substring(0, idx + 1);
    }

    const response = await this.yamcs.getInstanceClient()!.getObject('displays', this.data.name);
    const blob = await response.blob();

    const newObjectName = (prefix || '') + this.filenameForm.get('name')!.value;
    await this.yamcs.getInstanceClient()!.uploadObject('displays', newObjectName, blob);
    await this.yamcs.getInstanceClient()!.deleteObject('displays', this.data.name);
    this.dialogRef.close(newObjectName);
  }
}
