import {
  ChangeDetectionStrategy,
  Component,
  Inject,
  OnDestroy,
} from '@angular/core';
import { FormArray, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import {
  MessageService,
  SaveTimelineBandRequest,
  TimelineBand,
  WebappSdkModule,
  YamcsService,
} from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';
import { EditCommandBandComponent } from '../band-forms/edit-command-band.component';
import { EditItemBandComponent } from '../band-forms/edit-item-band.component';
import { EditParameterPlotComponent } from '../band-forms/edit-parameter-plot.component';
import { EditParameterStatesComponent } from '../band-forms/edit-parameter-states.component';
import { EditSpacerComponent } from '../band-forms/edit-spacer.component';
import { EditTimeRulerComponent } from '../band-forms/edit-time-ruler.component';
import { removeUnsetProperties } from '../bands/properties';

@Component({
  templateUrl: './edit-band-dialog.component.html',
  styleUrl: './edit-band-dialog.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    EditCommandBandComponent,
    EditItemBandComponent,
    EditParameterPlotComponent,
    EditParameterStatesComponent,
    EditSpacerComponent,
    EditTimeRulerComponent,
    WebappSdkModule,
  ],
})
export class EditBandDialogComponent implements OnDestroy {
  form: FormGroup;
  dirty$ = new BehaviorSubject<boolean>(false);

  private formSubscription: Subscription;

  constructor(
    private dialogRef: MatDialogRef<EditBandDialogComponent>,
    readonly yamcs: YamcsService,
    private messageService: MessageService,
    formBuilder: FormBuilder,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    const band = data.band as TimelineBand;
    this.form = formBuilder.group({
      name: [band.name, [Validators.required]],
      description: [band.description || ''],
      tags: [band.tags || []],
      traces: formBuilder.array([]), // Used by parameter plot
      valueMappings: formBuilder.array([]), // Used by parameter states
      properties: formBuilder.group({}), // Properties are added in sub-components
    });
    this.formSubscription = this.form.valueChanges.subscribe(() => {
      this.dirty$.next(true);
    });
  }

  doOnConfirm() {
    const formValue = this.form.value;
    const options: SaveTimelineBandRequest = {
      type: this.data.band.type,
      name: formValue.name,
      description: formValue.description,
      shared: this.data.band.shared,
      tags: formValue.tags,
      properties: formValue.properties,
    };

    for (let i = 0; i < this.traces.length; i++) {
      const traceForm = this.traces.at(i) as FormGroup;
      for (const key in traceForm.controls) {
        const propName = `trace_${i + 1}_${key}`;
        const value = traceForm.controls[key].value;
        options.properties![propName] = value;
      }
    }

    for (let i = 0; i < this.valueMappings.length; i++) {
      const mappingForm = this.valueMappings.at(i) as FormGroup;
      for (const key in mappingForm.controls) {
        const propName = `value_mapping_${i}_${key}`;
        const value = mappingForm.controls[key].value;
        options.properties![propName] = value;
      }
    }

    removeUnsetProperties(options.properties || {});

    this.yamcs.yamcsClient
      .saveTimelineBand(this.yamcs.instance!, this.data.band.id, options)
      .then((band) => this.dialogRef.close(band))
      .catch((err) => this.messageService.showError(err));
  }

  get traces() {
    return this.form.controls['traces'] as FormArray;
  }

  get valueMappings() {
    return this.form.controls['valueMappings'] as FormArray;
  }

  ngOnDestroy() {
    this.formSubscription?.unsubscribe();
  }
}
