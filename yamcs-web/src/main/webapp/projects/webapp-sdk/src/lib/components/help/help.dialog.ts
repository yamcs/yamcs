import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';

@Component({
  selector: 'ya-help-dialog',
  templateUrl: './help.dialog.html',
})
export class HelpDialog {

  constructor(@Inject(MAT_DIALOG_DATA) readonly data: any) {
  }
}
