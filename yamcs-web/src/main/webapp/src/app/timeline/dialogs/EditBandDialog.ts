import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { TimelineBand } from '../../client/types/timeline';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './EditBandDialog.html',
  styleUrls: ['./EditBandDialog.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditBandDialog {

  constructor(
    private dialogRef: MatDialogRef<EditBandDialog>,
    readonly yamcs: YamcsService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) { }

  onConfirm(band: TimelineBand) {
    this.dialogRef.close(band);
  }

  onCancel() {
    this.dialogRef.close();
  }
}
