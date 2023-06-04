import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';

@Component({
  selector: 'app-argument-enum-dialog',
  templateUrl: './ArgumentEnumDialog.html',
})
export class ArgumentEnumDialog {

  constructor(
    @Inject(MAT_DIALOG_DATA) readonly data: any) {
  }
}
