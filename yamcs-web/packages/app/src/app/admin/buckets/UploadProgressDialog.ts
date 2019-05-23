import { ChangeDetectionStrategy, Component, ElementRef, Inject, ViewChild } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material';
import { Observable } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { Upload } from './Upload';

@Component({
  selector: 'app-upload-progress-dialog',
  templateUrl: './UploadProgressDialog.html',
  styleUrls: ['./UploadProgressDialog.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UploadProgressDialog {

  uploads$: Observable<Upload[]>;

  @ViewChild('tableWrapper')
  tableWrapper: ElementRef;

  constructor(
    dialogRef: MatDialogRef<UploadProgressDialog>,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    this.uploads$ = data.uploads$.pipe(
      debounceTime(500), // limit updates in the case of batch uploads
    );
  }
}
