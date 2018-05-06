import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA } from '@angular/material';

@Component({
  selector: 'app-help-dialog',
  templateUrl: './HelpDialog.html',
})
export class HelpDialog {

  constructor(@Inject(MAT_DIALOG_DATA) readonly data: any) {
  }
}
