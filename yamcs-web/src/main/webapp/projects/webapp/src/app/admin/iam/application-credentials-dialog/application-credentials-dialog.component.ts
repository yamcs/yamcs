import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-application-credentials-dialog',
  templateUrl: './application-credentials-dialog.component.html',
  imports: [WebappSdkModule],
})
export class ApplicationCredentialsDialogComponent {
  constructor(@Inject(MAT_DIALOG_DATA) readonly data: any) {}
}
