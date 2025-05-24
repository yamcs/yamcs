import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { NonNullableFormBuilder, Validators } from '@angular/forms';
import { MatDialogRef } from '@angular/material/dialog';
import { Router } from '@angular/router';
import {
  MessageService,
  WebappSdkModule,
  YamcsService,
} from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-create-view-dialog',
  templateUrl: './create-view-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [WebappSdkModule],
})
export class CreateViewDialogComponent {
  private dialogRef = inject(MatDialogRef<CreateViewDialogComponent>);
  private formBuilder = inject(NonNullableFormBuilder);
  private messageService = inject(MessageService);
  private router = inject(Router);
  private yamcs = inject(YamcsService);

  form = this.formBuilder.group({
    name: ['', Validators.required],
  });

  isInvalid() {
    return !this.form.valid;
  }

  createView() {
    if (this.isInvalid()) {
      return;
    }

    const formValue = this.form.value;
    this.yamcs.yamcsClient
      .createTimelineView(this.yamcs.instance!, {
        name: formValue.name!,
        bands: [],
      })
      .then((view) => {
        const url = `/timeline?c=${this.yamcs.context}&view=${view.id}`;
        this.router
          .navigateByUrl('/', { skipLocationChange: true })
          .then(() => this.router.navigateByUrl(url));
      })
      .catch((err) => this.messageService.showError(err));
    this.dialogRef.close();
  }
}
