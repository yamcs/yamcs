import { ChangeDetectionStrategy, Component, Inject, OnDestroy } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MessageService, UpdateTimelineBandRequest, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';
import { EditCommandBandComponent } from '../command-band/edit-command-band/edit-command-band.component';
import { EditItemBandComponent } from '../item-band/edit-item-band/edit-item-band.component';
import { EditSpacerComponent } from '../spacer/edit-spacer/edit-spacer.component';
import { EditTimeRulerComponent } from '../time-ruler/edit-time-ruler/edit-time-ruler.component';

@Component({
  standalone: true,
  templateUrl: './edit-band-dialog.component.html',
  styleUrl: './edit-band-dialog.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    EditCommandBandComponent,
    EditItemBandComponent,
    EditSpacerComponent,
    EditTimeRulerComponent,
    WebappSdkModule,
  ],
})
export class EditBandDialogComponent implements OnDestroy {

  form: UntypedFormGroup;
  dirty$ = new BehaviorSubject<boolean>(false);

  private formSubscription: Subscription;

  constructor(
    private dialogRef: MatDialogRef<EditBandDialogComponent>,
    readonly yamcs: YamcsService,
    private messageService: MessageService,
    formBuilder: UntypedFormBuilder,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    const band = data.band;
    this.form = formBuilder.group({
      name: [band.name, [Validators.required]],
      description: [band.description || ''],
      tags: [band.tags || []],
      properties: formBuilder.group({}), // Properties are added in sub-components
    });
    this.formSubscription = this.form.valueChanges.subscribe(() => {
      this.dirty$.next(true);
    });
  }

  doOnConfirm() {
    const formValue = this.form.value;
    const options: UpdateTimelineBandRequest = {
      name: formValue.name,
      description: formValue.description,
      shared: this.data.band.shared,
      tags: formValue.tags,
      properties: formValue.properties,
    };

    this.yamcs.yamcsClient.updateTimelineBand(this.yamcs.instance!, this.data.band.id, options)
      .then(band => this.dialogRef.close(band))
      .catch(err => this.messageService.showError(err));
  }

  ngOnDestroy() {
    this.formSubscription?.unsubscribe();
  }
}
