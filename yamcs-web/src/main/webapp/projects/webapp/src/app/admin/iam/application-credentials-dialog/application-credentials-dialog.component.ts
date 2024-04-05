import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-application-credentials-dialog',
  templateUrl: './application-credentials-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class ApplicationCredentialsDialogComponent {

  constructor(@Inject(MAT_DIALOG_DATA) readonly data: any) {
  }
}
