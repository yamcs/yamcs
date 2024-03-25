import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { SharedModule } from '../../../shared/SharedModule';

@Component({
  standalone: true,
  selector: 'app-view-object-metadata-dialog',
  templateUrl: './view-object-metadata-dialog.component.html',
  imports: [
    SharedModule,
  ],
})
export class ViewObjectMetadataDialogComponent {

  constructor(
    private dialogRef: MatDialogRef<ViewObjectMetadataDialogComponent>,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) { }
}
