import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

@Component({
  selector: 'app-view-object-metadata-dialog',
  templateUrl: './ViewObjectMetadataDialog.html',
})
export class ViewObjectMetadataDialog {

  constructor(
    private dialogRef: MatDialogRef<ViewObjectMetadataDialog>,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) { }
}
