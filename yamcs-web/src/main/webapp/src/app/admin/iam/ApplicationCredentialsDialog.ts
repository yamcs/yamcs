import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import { MAT_LEGACY_DIALOG_DATA } from '@angular/material/legacy-dialog';

@Component({
  selector: 'app-application-credentials-dialog',
  templateUrl: './ApplicationCredentialsDialog.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ApplicationCredentialsDialog {

  constructor(@Inject(MAT_LEGACY_DIALOG_DATA) readonly data: any) {
  }
}
