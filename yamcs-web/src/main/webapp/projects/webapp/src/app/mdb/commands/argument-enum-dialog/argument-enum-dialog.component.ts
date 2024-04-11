import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-argument-enum-dialog',
  templateUrl: './argument-enum-dialog.component.html',
  imports: [
    WebappSdkModule,
  ],
})
export class ArgumentEnumDialogComponent {

  constructor(
    @Inject(MAT_DIALOG_DATA) readonly data: any) {
  }
}
