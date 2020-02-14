import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';

@Component({
  selector: 'app-application-credentials-dialog',
  templateUrl: './ApplicationCredentialsDialog.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ApplicationCredentialsDialog {

  constructor(@Inject(MAT_DIALOG_DATA) readonly data: any) {
  }
}
