import { Component, Inject } from '@angular/core';
import { MAT_LEGACY_DIALOG_DATA } from '@angular/material/legacy-dialog';

@Component({
  selector: 'app-argument-enum-dialog',
  templateUrl: './ArgumentEnumDialog.html',
})
export class ArgumentEnumDialog {

  constructor(
    @Inject(MAT_LEGACY_DIALOG_DATA) readonly data: any) {
  }
}
