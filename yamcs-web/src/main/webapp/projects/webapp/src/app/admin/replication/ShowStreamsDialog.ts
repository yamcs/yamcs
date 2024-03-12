import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';

@Component({
  selector: 'app-show-streams-dialog',
  templateUrl: './ShowStreamsDialog.html',
})
export class ShowStreamsDialog {

  constructor(
    @Inject(MAT_DIALOG_DATA) readonly data: any) {
  }
}
