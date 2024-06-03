import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-session-expired-dialog',
  templateUrl: './session-expired-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class SessionExpiredDialogComponent {

  constructor(@Inject(MAT_DIALOG_DATA) public data: any) {
  }

  reload() {
    window.location.reload();
  }
}
