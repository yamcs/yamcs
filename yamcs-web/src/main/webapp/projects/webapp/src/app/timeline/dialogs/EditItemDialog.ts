import { ChangeDetectionStrategy, Component, Inject, OnDestroy } from '@angular/core';
import { FormGroup, UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MessageService, SelectOption, UpdateTimelineItemRequest, YamcsService, utils } from '@yamcs/webapp-sdk';
import { Subscription } from 'rxjs';
import { itemPropertyInfo } from '../itemBand/ItemBand';
import { resolveProperties } from '../properties';

const OVERRIDE_SUFFIX = '_overrideBand';

@Component({
  templateUrl: './EditItemDialog.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditItemDialog implements OnDestroy {

  resolutionOptions: SelectOption[] = [
    { id: 'seconds', label: 'seconds' },
    { id: 'minutes', label: 'minutes' },
    { id: 'hours', label: 'hours' }
  ];
  startConstraintOptions: SelectOption[] = [
    { id: 'START_ON', label: 'Start on' },
  ];

  form: UntypedFormGroup;

  private formSubscription: Subscription;

  constructor(
    private dialogRef: MatDialogRef<EditItemDialog>,
    formBuilder: UntypedFormBuilder,
    private yamcs: YamcsService,
    private messageService: MessageService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    const item = data.item;
    const itemProperties = item.properties || {};
    const props = resolveProperties(itemPropertyInfo, itemProperties);

    this.form = formBuilder.group({
      name: [item.name, Validators.required],
      start: [item.start, Validators.required],
      duration: [item.duration, Validators.required],
      tags: [item.tags || [], []],
      properties: formBuilder.group({
        backgroundColor: [props.backgroundColor, []],
        backgroundColor_overrideBand: 'backgroundColor' in itemProperties,
        borderColor: [props.borderColor, []],
        borderColor_overrideBand: 'borderColor' in itemProperties,
        borderWidth: [props.borderWidth, []],
        borderWidth_overrideBand: 'borderWidth' in itemProperties,
        cornerRadius: [props.cornerRadius, []],
        cornerRadius_overrideBand: 'cornerRadius' in itemProperties,
        marginLeft: [props.marginLeft, []],
        marginLeft_overrideBand: 'marginLeft' in itemProperties,
        textColor: [props.textColor, []],
        textColor_overrideBand: 'textColor' in itemProperties,
        textSize: [props.textSize, []],
        textSize_overrideBand: 'textSize' in itemProperties,
      }),
    });

    this.updateDisabledState();
    this.formSubscription = this.form.valueChanges.subscribe(() => {
      this.updateDisabledState();
    });
  }

  save() {
    const formValue = this.form.value;
    const options: UpdateTimelineItemRequest = {
      name: formValue.name,
      start: utils.toISOString(formValue.start),
      duration: formValue.duration,
      tags: formValue.tags,
      properties: formValue.properties,
    };
    this.yamcs.yamcsClient.updateTimelineItem(this.yamcs.instance!, this.data.item.id, options).then(item => this.dialogRef.close(item))
      .catch(err => this.messageService.showError(err));
  }

  delete() {
    if (confirm(`Are you sure you want to delete this item?`)) {
      this.yamcs.yamcsClient.deleteTimelineItem(this.yamcs.instance!, this.data.item.id)
        .then(() => this.dialogRef.close())
        .catch(err => this.messageService.showError(err));
    }
  }

  private updateDisabledState() {
    const { controls } = this.propertiesGroup;
    for (const key in controls) {
      if (key.endsWith(OVERRIDE_SUFFIX)) {
        const styleControl = controls[key.substring(0, key.length - OVERRIDE_SUFFIX.length)];
        if (controls[key].value) {
          styleControl.enable({ onlySelf: true });
        } else {
          styleControl.disable({ onlySelf: true });
        }
      }
    }
  }

  get propertiesGroup(): FormGroup {
    return this.form.controls['properties'] as FormGroup;
  }

  ngOnDestroy() {
    this.formSubscription?.unsubscribe();
  }
}
