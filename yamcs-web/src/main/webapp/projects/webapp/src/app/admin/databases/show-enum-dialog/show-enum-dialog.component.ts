import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-show-enum-dialog',
  templateUrl: './show-enum-dialog.component.html',
  imports: [WebappSdkModule],
})
export class ShowEnumDialogComponent {
  constructor(@Inject(MAT_DIALOG_DATA) readonly data: any) {}
}
