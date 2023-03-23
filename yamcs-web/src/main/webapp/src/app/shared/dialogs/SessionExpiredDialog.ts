import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import { MAT_LEGACY_DIALOG_DATA } from '@angular/material/legacy-dialog';

@Component({
  selector: 'app-session-expired-dialog',
  templateUrl: './SessionExpiredDialog.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SessionExpiredDialog {

  constructor(@Inject(MAT_LEGACY_DIALOG_DATA) public data: any) {
  }

  reload() {
    window.location.reload();
  }
}
