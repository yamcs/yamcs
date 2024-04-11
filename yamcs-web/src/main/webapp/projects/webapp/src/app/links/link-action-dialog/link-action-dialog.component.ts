import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { ActionInfo, WebappSdkModule } from '@yamcs/webapp-sdk';
import { BooleanOptionComponent } from './options/boolean-option/boolean-option.component';
import { FloatOptionComponent } from './options/float-option/float-option.component';
import { IntegerOptionComponent } from './options/integer-option/integer-option.component';
import { StringOptionComponent } from './options/string-option/string-option.component';

export interface LinkActionDialogData {
  action: ActionInfo;
}

@Component({
  standalone: true,
  templateUrl: './link-action-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    BooleanOptionComponent,
    FloatOptionComponent,
    IntegerOptionComponent,
    WebappSdkModule,
    StringOptionComponent,
  ],
})
export class LinkActionDialogComponent {

  form: FormGroup;

  constructor(
    private dialogRef: MatDialogRef<LinkActionDialogComponent>,
    @Inject(MAT_DIALOG_DATA) readonly data: LinkActionDialogData,
  ) {
    this.form = new FormGroup({});
    for (const option of this.options) {
      const control = new FormControl<string | null>(option.default ?? null);
      this.form.addControl(option.name, control);
    }
  }

  get options() {
    return this.data.action.spec?.options || [];
  }

  sendRequest() {
    const value = this.form.value;
    this.dialogRef.close(value);
  }
}
