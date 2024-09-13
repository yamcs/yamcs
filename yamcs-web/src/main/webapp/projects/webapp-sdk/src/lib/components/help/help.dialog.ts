import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogActions, MatDialogClose, MatDialogContent, MatDialogTitle } from '@angular/material/dialog';
import { MatIcon } from '@angular/material/icon';
import { YaButton } from '../button/button.component';

@Component({
  standalone: true,
  selector: 'ya-help-dialog',
  templateUrl: './help.dialog.html',
  styleUrl: './help.dialog.css',
  imports: [
    MatDialogActions,
    MatDialogClose,
    MatDialogContent,
    MatDialogTitle,
    MatIcon,
    YaButton,
  ],
})
export class YaHelpDialog {

  constructor(@Inject(MAT_DIALOG_DATA) readonly data: any) {
  }
}
