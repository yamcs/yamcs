import { ChangeDetectionStrategy, Component, Inject, OnDestroy } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { BehaviorSubject, Subscription } from 'rxjs';
import { TimelineBand, UpdateTimelineViewRequest } from '../../client/types/timeline';
import { MessageService } from '../../core/services/MessageService';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  selector: 'app-edit-view-dialog',
  templateUrl: './EditViewDialog.html',
  styleUrls: ['./EditViewDialog.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditViewDialog implements OnDestroy {

  form: FormGroup;

  dirty$ = new BehaviorSubject<boolean>(false);
  private formSubscription: Subscription;

  constructor(
    private dialogRef: MatDialogRef<EditViewDialog>,
    readonly yamcs: YamcsService,
    formBuilder: FormBuilder,
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
    if (this.formSubscription) {
      this.formSubscription.unsubscribe();
    }
  }
}
