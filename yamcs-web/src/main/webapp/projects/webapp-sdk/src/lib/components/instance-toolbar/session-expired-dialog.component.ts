import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogTitle,
} from '@angular/material/dialog';
import { MatIcon } from '@angular/material/icon';
import { YaButton } from '../button/button.component';

@Component({
  selector: 'ya-session-expired-dialog',
  templateUrl: './session-expired-dialog.component.html',
  styleUrl: './session-expired-dialog.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatDialogActions,
    MatDialogContent,
    MatDialogClose,
    MatDialogTitle,
    MatIcon,
    YaButton,
  ],
})
export class SessionExpiredDialogComponent {
  constructor(@Inject(MAT_DIALOG_DATA) public data: any) {}

  reload() {
    window.location.reload();
  }
}
