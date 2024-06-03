import { ChangeDetectionStrategy, Component, Inject, OnDestroy } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MessageService, TimelineBand, UpdateTimelineViewRequest, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';
import { BandMultiSelectComponent } from '../shared/band-multi-select/band-multi-select.component';

@Component({
  standalone: true,
  selector: 'app-edit-view-dialog',
  templateUrl: './edit-view-dialog.component.html',
  styleUrl: './edit-view-dialog.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    BandMultiSelectComponent,
    WebappSdkModule,
  ],
})
export class EditViewDialogComponent implements OnDestroy {

  form: UntypedFormGroup;

  dirty$ = new BehaviorSubject<boolean>(false);
  private formSubscription: Subscription;

  constructor(
    private dialogRef: MatDialogRef<EditViewDialogComponent>,
    readonly yamcs: YamcsService,
    formBuilder: UntypedFormBuilder,
    private messageService: MessageService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    const view = data.view;
    this.form = formBuilder.group({
      name: [view.name, Validators.required],
      bands: [view.bands || [], []],
    });
    this.formSubscription = this.form.valueChanges.subscribe(() => {
      this.dirty$.next(true);
    });
  }

  onConfirm() {
    const formValue = this.form.value;
    const options: UpdateTimelineViewRequest = {
      name: formValue.name,
      bands: formValue.bands.map((band: TimelineBand) => band.id),
    };
    const view = this.data.view;
    this.yamcs.yamcsClient.updateTimelineView(this.yamcs.instance!, view.id, options)
      .then(updatedView => this.dialogRef.close(updatedView))
      .catch(err => this.messageService.showError(err));
  }

  ngOnDestroy() {
    this.formSubscription?.unsubscribe();
  }
}
