import { Component, Inject } from '@angular/core';
import { MAT_LEGACY_DIALOG_DATA } from '@angular/material/legacy-dialog';

@Component({
  selector: 'app-help-dialog',
  templateUrl: './HelpDialog.html',
})
export class HelpDialog {

  constructor(@Inject(MAT_LEGACY_DIALOG_DATA) readonly data: any) {
  }
}
