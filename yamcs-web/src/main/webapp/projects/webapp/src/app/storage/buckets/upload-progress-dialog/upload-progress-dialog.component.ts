import { ChangeDetectionStrategy, Component, ElementRef, Inject, ViewChild } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Observable } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { SharedModule } from '../../../shared/SharedModule';
import { Upload } from './Upload';

@Component({
  standalone: true,
  selector: 'app-upload-progress-dialog',
  templateUrl: './upload-progress-dialog.component.html',
  styleUrl: './upload-progress-dialog.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    SharedModule,
  ],
})
export class UploadProgressDialogComponent {

  uploads$: Observable<Upload[]>;

  @ViewChild('tableWrapper', { static: true })
  tableWrapper: ElementRef;

  constructor(
    dialogRef: MatDialogRef<UploadProgressDialogComponent>,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    this.uploads$ = data.uploads$.pipe(
      debounceTime(500), // limit updates in the case of batch uploads
    );
  }
}
