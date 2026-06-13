import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-argument-enum-dialog',
  templateUrl: './argument-enum-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [WebappSdkModule],
})
export class ArgumentEnumDialogComponent {
  constructor(@Inject(MAT_DIALOG_DATA) readonly data: any) {}
}
