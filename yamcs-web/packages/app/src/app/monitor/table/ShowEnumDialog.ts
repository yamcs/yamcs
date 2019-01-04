import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA } from '@angular/material';

@Component({
  selector: 'app-show-enum-dialog',
  templateUrl: './ShowEnumDialog.html',
})
export class ShowEnumDialog {

  constructor(
    @Inject(MAT_DIALOG_DATA) readonly data: any) {
  }
}
